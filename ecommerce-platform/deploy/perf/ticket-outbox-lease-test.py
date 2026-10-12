#!/usr/bin/env python3
"""Verify outbox publisher leases, crash recovery, and claim fencing."""

import argparse
import json
import subprocess
import time
import uuid


def mysql_exec(mysql, host, port, user, password, database, sql):
    cmd = [
        mysql,
        f"--host={host}",
        f"--port={port}",
        f"--user={user}",
        "--batch",
        "--skip-column-names",
        "--raw",
    ]
    if password:
        cmd.append(f"--password={password}")
    cmd.extend([database, "-e", sql])
    completed = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip())
    return completed.stdout.strip()


def insert_outbox(mysql_args, event_type, status, claim_token, lease_offset_sql):
    marker = uuid.uuid4().hex
    payload = json.dumps(
        {
            "runId": f"-{uuid.uuid4().int % 1000000000}",
            "runNo": f"LEASE-{marker}",
            "ticketNo": f"LEASE-{marker}",
            "requestedAt": "2026-10-11T00:00:00",
        },
        ensure_ascii=True,
        separators=(",", ":"),
    ).replace("'", "''")
    sql = f"""
        INSERT INTO sp_support_outbox
            (aggregate_type, aggregate_id, event_type, transport, destination,
             payload, status, retry_count, next_retry_at, claim_token,
             lease_expires_at, last_error, created_at, updated_at)
        VALUES
            ('AGENT_RUN', 0, '{event_type}', 'RABBIT', 'support.agent.exchange',
             '{payload}', '{status}', 0, DATE_SUB(NOW(), INTERVAL 5 SECOND),
             '{claim_token}', {lease_offset_sql}, NULL, NOW(), NOW());
        SELECT LAST_INSERT_ID();
    """
    output = mysql_exec(*mysql_args, sql)
    return int(output.splitlines()[-1].strip()), marker


def query_outbox(mysql_args, outbox_id):
    sql = f"""
        SELECT status, COALESCE(claim_token, ''), COALESCE(lease_expires_at, '')
        FROM sp_support_outbox
        WHERE id = {int(outbox_id)};
    """
    output = mysql_exec(*mysql_args, sql)
    if not output:
        return None
    return output.splitlines()[0].split("\t")


def wait_for_status(mysql_args, outbox_id, expected, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    last = None
    while time.monotonic() < deadline:
        last = query_outbox(mysql_args, outbox_id)
        if last and last[0] == expected:
            return last
        time.sleep(0.2)
    return last


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mysql",
        default=r"mysql",
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="3306")
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="change-me")
    parser.add_argument("--database", default="ecommerce")
    parser.add_argument("--timeout-seconds", type=int, default=15)
    args = parser.parse_args()
    mysql_args = (
        args.mysql,
        args.host,
        args.port,
        args.user,
        args.password,
        args.database,
    )

    inserted_ids = []
    checks = {}
    try:
        stale_id, stale_marker = insert_outbox(
            mysql_args,
            "AGENT_RUN_CANCEL_REQUESTED",
            "SENDING",
            "stale-token",
            "DATE_SUB(NOW(), INTERVAL 5 SECOND)",
        )
        inserted_ids.append(stale_id)
        checks["staleSendingRecovered"] = wait_for_status(
            mysql_args,
            stale_id,
            "SENT",
            args.timeout_seconds,
        )

        active_id, active_marker = insert_outbox(
            mysql_args,
            "AGENT_RUN_CANCEL_REQUESTED",
            "SENDING",
            "active-token",
            "DATE_ADD(NOW(), INTERVAL 30 SECOND)",
        )
        inserted_ids.append(active_id)
        time.sleep(4)
        checks["activeLeaseProtected"] = query_outbox(mysql_args, active_id)
        sql = f"""
            UPDATE sp_support_outbox
            SET lease_expires_at = DATE_SUB(NOW(), INTERVAL 5 SECOND),
                updated_at = NOW()
            WHERE id = {active_id};
        """
        mysql_exec(*mysql_args, sql)
        checks["expiredLeaseRecovered"] = wait_for_status(
            mysql_args,
            active_id,
            "SENT",
            args.timeout_seconds,
        )

        pending_id, pending_marker = insert_outbox(
            mysql_args,
            "AGENT_RUN_CANCEL_REQUESTED",
            "PENDING",
            "",
            "NULL",
        )
        inserted_ids.append(pending_id)
        checks["pendingPublished"] = wait_for_status(
            mysql_args,
            pending_id,
            "SENT",
            args.timeout_seconds,
        )

        passed = (
            checks["staleSendingRecovered"] is not None
            and checks["staleSendingRecovered"][0] == "SENT"
            and checks["activeLeaseProtected"] is not None
            and checks["activeLeaseProtected"][0] == "SENDING"
            and checks["activeLeaseProtected"][1] == "active-token"
            and checks["expiredLeaseRecovered"] is not None
            and checks["expiredLeaseRecovered"][0] == "SENT"
            and checks["pendingPublished"] is not None
            and checks["pendingPublished"][0] == "SENT"
        )
        report = {
            "passed": passed,
            "staleSendingId": stale_id,
            "activeLeaseId": active_id,
            "pendingId": pending_id,
            "checks": checks,
        }
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if passed else 1
    finally:
        if inserted_ids:
            ids = ",".join(str(item) for item in inserted_ids)
            mysql_exec(
                *mysql_args,
                f"DELETE FROM sp_support_outbox WHERE id IN ({ids});",
            )


if __name__ == "__main__":
    raise SystemExit(main())
