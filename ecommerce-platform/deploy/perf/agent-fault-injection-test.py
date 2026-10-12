#!/usr/bin/env python3
"""Run isolated Redis, MySQL, and RabbitMQ failure scenarios."""

import argparse
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def request_json(base_url, path, method="GET", body=None, token=None, timeout=15):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read().decode("utf-8", errors="replace")
            return response.status, json.loads(text)
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(text)
        except Exception:
            payload = {"raw": text}
        return exc.code, payload
    except Exception as exc:
        return None, {"transportError": str(exc)}


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


def login(base_url, username, password):
    return request_json(
        base_url,
        "/api/auth/login",
        method="POST",
        body={"username": username, "password": password},
    )


def test_redis_unavailable(base_url, username, password):
    login_status, login_payload = login(base_url, username, password)
    token = (login_payload.get("data") or {}).get("accessToken")
    if login_status != 200 or not token:
        return {
            "passed": False,
            "reason": "login failed while only Redis was unavailable",
            "loginStatus": login_status,
            "loginPayload": login_payload,
        }
    chat_status, chat_payload = request_json(
        base_url,
        "/api/chat",
        method="POST",
        token=token,
        body={"message": "fault-injection-redis"},
        timeout=20,
    )
    return {
        "passed": (
            chat_status == 200
            and chat_payload.get("code") == 429
            and "guard unavailable" in chat_payload.get("message", "").lower()
        ),
        "loginHttpStatus": login_status,
        "chatHttpStatus": chat_status,
        "chatPayload": chat_payload,
    }


def test_mysql_unavailable(base_url, username, password):
    health_status, health_payload = request_json(
        base_url,
        "/actuator/health",
        timeout=20,
    )
    login_status, login_payload = login(base_url, username, password)
    second_health_status, second_health_payload = request_json(
        base_url,
        "/actuator/health",
        timeout=20,
    )
    return {
        "passed": (
            (health_status in {200, 503})
            and health_payload.get("status") == "DOWN"
            and (login_status == 500 or login_payload.get("code") == 500)
            and second_health_status in {200, 503}
        ),
        "healthHttpStatus": health_status,
        "healthPayload": health_payload,
        "loginHttpStatus": login_status,
        "loginPayload": login_payload,
        "secondHealthHttpStatus": second_health_status,
        "secondHealthPayload": second_health_payload,
    }


def insert_outbox(mysql_args):
    marker = uuid.uuid4().hex
    payload = json.dumps(
        {
            "runId": f"-{uuid.uuid4().int % 1000000000}",
            "runNo": f"FAULT-{marker}",
            "ticketNo": f"FAULT-{marker}",
        },
        separators=(",", ":"),
    ).replace("'", "''")
    sql = f"""
        INSERT INTO sp_support_outbox
            (aggregate_type, aggregate_id, event_type, transport, destination,
             payload, status, retry_count, next_retry_at, claim_token,
             lease_expires_at, last_error, created_at, updated_at)
        VALUES
            ('AGENT_RUN', 0, 'AGENT_RUN_CANCEL_REQUESTED', 'RABBIT',
             'support.agent.exchange', '{payload}', 'PENDING', 0, NOW(),
             NULL, NULL, NULL, NOW(), NOW());
        SELECT LAST_INSERT_ID();
    """
    output = mysql_exec(*mysql_args, sql)
    return int(output.splitlines()[-1].strip())


def outbox_state(mysql_args, outbox_id):
    sql = f"""
        SELECT status, retry_count, COALESCE(last_error, '')
        FROM sp_support_outbox
        WHERE id = {int(outbox_id)};
    """
    output = mysql_exec(*mysql_args, sql)
    if not output:
        return None
    return output.splitlines()[0].split("\t")


def wait_for_rabbit_retry(mysql_args, outbox_id, timeout_seconds):
    deadline = time.monotonic() + timeout_seconds
    last = None
    while time.monotonic() < deadline:
        last = outbox_state(mysql_args, outbox_id)
        if last and int(last[1]) >= 1 and last[2]:
            return last
        time.sleep(0.5)
    return last


def test_rabbit_unavailable(mysql_args, timeout_seconds):
    outbox_id = insert_outbox(mysql_args)
    try:
        state = wait_for_rabbit_retry(mysql_args, outbox_id, timeout_seconds)
        return {
            "passed": (
                state is not None
                and state[0] == "PENDING"
                and int(state[1]) >= 1
                and bool(state[2])
            ),
            "outboxId": outbox_id,
            "state": state,
        }
    finally:
        mysql_exec(
            *mysql_args,
            f"DELETE FROM sp_support_outbox WHERE id = {outbox_id};",
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--redis-base-url", default="http://127.0.0.1:8083")
    parser.add_argument("--mysql-base-url", default="http://127.0.0.1:8084")
    parser.add_argument("--username", default="testuser")
    parser.add_argument("--password", default="change-me")
    parser.add_argument(
        "--mysql",
        default=r"mysql",
    )
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", default="3306")
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="change-me")
    parser.add_argument("--database", default="ecommerce")
    parser.add_argument("--timeout-seconds", type=int, default=30)
    args = parser.parse_args()

    report = {
        "redisUnavailable": test_redis_unavailable(
            args.redis_base_url,
            args.username,
            args.password,
        ),
        "mysqlUnavailable": test_mysql_unavailable(
            args.mysql_base_url,
            args.username,
            args.password,
        ),
        "rabbitUnavailable": test_rabbit_unavailable(
            (
                args.mysql,
                args.mysql_host,
                args.mysql_port,
                args.mysql_user,
                args.mysql_password,
                args.database,
            ),
            args.timeout_seconds,
        ),
    }
    report["passed"] = all(
        report[key].get("passed")
        for key in ("redisUnavailable", "mysqlUnavailable", "rabbitUnavailable")
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
