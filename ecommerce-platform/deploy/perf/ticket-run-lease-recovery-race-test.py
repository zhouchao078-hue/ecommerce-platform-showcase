#!/usr/bin/env python3
"""Verify concurrent AgentRun lease recovery is claimed exactly once."""

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


def insert_expired_runs(mysql_args, count):
    marker = uuid.uuid4().hex
    ticket_no = f"LEASE-RACE-{marker}"
    payload = '{"tenant_id":"ecommerce","content":"lease recovery race"}'
    run_values = []
    for index in range(count):
        run_no = f"LR-{marker}-{index:03d}"
        run_values.append(
            "(%s, @ticket_id, 'RUNNING', '%s', 0, 0, NULL, 'dead-worker', "
            "DATE_SUB(NOW(), INTERVAL 10 SECOND), "
            "DATE_SUB(NOW(), INTERVAL 10 SECOND), NOW(), NOW(), NOW())"
            % (f"'{run_no}'", payload)
        )
    sql = f"""
        INSERT INTO sp_support_ticket
            (ticket_no, user_id, channel, category, priority, status,
             subject, created_at, updated_at)
        VALUES
            ('{ticket_no}', 1, 'fault-test', 'general', 'NORMAL', 10,
             'Lease recovery race', NOW(), NOW());
        SET @ticket_id = LAST_INSERT_ID();
        INSERT INTO sp_support_agent_run
            (run_no, ticket_id, status, request_payload, cancel_requested,
             version, last_completed_step, worker_id, lease_expires_at,
             last_heartbeat_at, started_at, created_at, updated_at)
        VALUES
            {",".join(run_values)};
        SELECT id
        FROM sp_support_agent_run
        WHERE run_no LIKE 'LR-{marker}-%'
        ORDER BY id;
    """
    output = mysql_exec(*mysql_args, sql)
    run_ids = [int(line.strip()) for line in output.splitlines() if line.strip()]
    return marker, ticket_no, run_ids


def scalar(mysql_args, sql):
    output = mysql_exec(*mysql_args, sql)
    return int(output.strip() or "0")


def grouped_counts(mysql_args, run_ids, table, where_column, extra_where):
    ids = ",".join(str(item) for item in run_ids)
    sql = f"""
        SELECT {where_column}, COUNT(*)
        FROM {table}
        WHERE {where_column} IN ({ids})
          AND {extra_where}
        GROUP BY {where_column};
    """
    output = mysql_exec(*mysql_args, sql)
    result = {}
    for line in output.splitlines():
        key, value = line.split("\t")
        result[int(key)] = int(value)
    return result


def wait_for_recovery(mysql_args, run_ids, timeout_seconds):
    ids = ",".join(str(item) for item in run_ids)
    deadline = time.monotonic() + timeout_seconds
    last = {}
    while time.monotonic() < deadline:
        pending = scalar(
            mysql_args,
            f"""
            SELECT COUNT(*)
            FROM sp_support_agent_run
            WHERE id IN ({ids})
              AND status = 'PENDING'
              AND worker_id IS NULL
              AND lease_expires_at IS NULL
              AND last_completed_step = 'LEASE_RECOVERED';
            """,
        )
        step_counts = grouped_counts(
            mysql_args,
            run_ids,
            "sp_support_agent_run_step",
            "run_id",
            "step_name = 'LEASE_RECOVERED' AND status = 'SUCCEEDED'",
        )
        recovered_outbox_counts = grouped_counts(
            mysql_args,
            run_ids,
            "sp_support_outbox",
            "aggregate_id",
            "event_type = 'AGENT_RUN_REQUESTED' "
            "AND payload LIKE '%\"recovered\":true%'",
        )
        last = {
            "pendingRuns": pending,
            "stepCounts": step_counts,
            "recoveredOutboxCounts": recovered_outbox_counts,
        }
        if (
            pending == len(run_ids)
            and len(step_counts) == len(run_ids)
            and all(value == 1 for value in step_counts.values())
            and len(recovered_outbox_counts) == len(run_ids)
            and all(value == 1 for value in recovered_outbox_counts.values())
        ):
            return last
        time.sleep(0.25)
    return last


def cleanup(mysql_args, ticket_no, run_ids):
    ids = ",".join(str(item) for item in run_ids) or "0"
    mysql_exec(
        *mysql_args,
        f"""
        DELETE FROM sp_support_outbox WHERE aggregate_id IN ({ids});
        DELETE FROM sp_support_agent_run_step WHERE run_id IN ({ids});
        DELETE FROM sp_support_agent_run_event WHERE run_id IN ({ids});
        DELETE FROM sp_support_agent_run WHERE id IN ({ids});
        DELETE FROM sp_support_ticket WHERE ticket_no = '{ticket_no}';
        """,
    )


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
    parser.add_argument("--runs", type=int, default=40)
    parser.add_argument("--timeout-seconds", type=int, default=30)
    args = parser.parse_args()
    mysql_args = (
        args.mysql,
        args.host,
        args.port,
        args.user,
        args.password,
        args.database,
    )

    marker, ticket_no, run_ids = insert_expired_runs(
        mysql_args,
        max(1, args.runs),
    )
    try:
        state = wait_for_recovery(
            mysql_args,
            run_ids,
            args.timeout_seconds,
        )
        step_counts = state.get("stepCounts", {})
        outbox_counts = state.get("recoveredOutboxCounts", {})
        passed = (
            state.get("pendingRuns") == len(run_ids)
            and all(step_counts.get(run_id) == 1 for run_id in run_ids)
            and all(outbox_counts.get(run_id) == 1 for run_id in run_ids)
        )
        report = {
            "passed": passed,
            "marker": marker,
            "runCount": len(run_ids),
            "pendingRuns": state.get("pendingRuns", 0),
            "duplicateStepRuns": [
                run_id for run_id, count in step_counts.items() if count != 1
            ],
            "duplicateOutboxRuns": [
                run_id for run_id, count in outbox_counts.items() if count != 1
            ],
            "missingStepRuns": [
                run_id for run_id in run_ids if step_counts.get(run_id) != 1
            ],
            "missingOutboxRuns": [
                run_id for run_id in run_ids if outbox_counts.get(run_id) != 1
            ],
        }
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if passed else 1
    finally:
        cleanup(mysql_args, ticket_no, run_ids)


if __name__ == "__main__":
    raise SystemExit(main())
