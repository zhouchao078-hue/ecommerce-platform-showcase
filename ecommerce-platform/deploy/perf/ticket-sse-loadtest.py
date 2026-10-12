#!/usr/bin/env python3
"""Open concurrent ticket-agent SSE connections and verify snapshot + heartbeat."""

import argparse
import http.client
import json
import subprocess
import threading
import time
import urllib.parse
import urllib.request
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
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status, json.loads(
            response.read().decode("utf-8", errors="replace")
        )


def login(base_url, username, password):
    status, payload = request_json(
        base_url,
        "/api/auth/login",
        method="POST",
        body={"username": username, "password": password},
    )
    token = (payload.get("data") or {}).get("accessToken")
    if status != 200 or not token:
        raise RuntimeError("Login failed: " + json.dumps(payload, ensure_ascii=False))
    return token


def insert_long_running_run(mysql_args):
    marker = uuid.uuid4().hex
    ticket_no = f"SSE-{marker}"
    run_no = f"SR-{marker}"
    sql = f"""
        INSERT INTO sp_support_ticket
            (ticket_no, user_id, channel, category, priority, status,
             subject, created_at, updated_at)
        VALUES
            ('{ticket_no}', 1, 'perf-test', 'general', 'NORMAL', 10,
             'SSE load test', NOW(), NOW());
        SET @ticket_id = LAST_INSERT_ID();
        INSERT INTO sp_support_agent_run
            (run_no, ticket_id, status, request_payload, cancel_requested,
             version, last_completed_step, worker_id, lease_expires_at,
             last_heartbeat_at, started_at, created_at, updated_at)
        VALUES
            ('{run_no}', @ticket_id, 'RUNNING', '{{}}', 0, 0,
             'WORKER_CLAIMED', 'sse-test-worker',
             DATE_ADD(NOW(), INTERVAL 1 HOUR), NOW(), NOW(), NOW(), NOW());
        SELECT LAST_INSERT_ID();
    """
    output = mysql_exec(*mysql_args, sql)
    return ticket_no, run_no, int(output.splitlines()[-1].strip())


def cleanup(mysql_args, ticket_no, run_id):
    mysql_exec(
        *mysql_args,
        f"""
        DELETE FROM sp_support_agent_run_event WHERE run_id = {run_id};
        DELETE FROM sp_support_agent_run_step WHERE run_id = {run_id};
        DELETE FROM sp_support_outbox WHERE aggregate_id = {run_id};
        DELETE FROM sp_support_agent_run WHERE id = {run_id};
        DELETE FROM sp_support_ticket WHERE ticket_no = '{ticket_no}';
        """,
    )


def consume_sse(base_url, run_id, token, timeout_seconds, barrier, result):
    parsed = urllib.parse.urlparse(base_url)
    host = parsed.hostname
    port = parsed.port or 80
    connection = None
    started = time.monotonic()
    saw_snapshot = False
    saw_ping = False
    try:
        barrier.wait(timeout=10)
        connection = http.client.HTTPConnection(host, port, timeout=timeout_seconds)
        connection.request(
            "GET",
            f"/api/ticket-agent/runs/{run_id}/events",
            headers={
                "Accept": "text/event-stream",
                "Authorization": "Bearer " + token,
            },
        )
        response = connection.getresponse()
        if response.status != 200:
            raise RuntimeError(f"SSE HTTP status {response.status}")
        event_name = ""
        deadline = started + timeout_seconds
        while time.monotonic() < deadline:
            line = response.readline().decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                continue
            if line.startswith("event:"):
                event_name = line.split(":", 1)[1].strip()
            elif line.startswith("data:"):
                if event_name == "run.status":
                    saw_snapshot = True
                elif event_name == "ping":
                    saw_ping = True
                    break
        result["sawSnapshot"] = saw_snapshot
        result["sawPing"] = saw_ping
        result["elapsedMs"] = int((time.monotonic() - started) * 1000)
        result["passed"] = saw_snapshot and saw_ping
    except Exception as exc:
        result["error"] = str(exc)
        result["elapsedMs"] = int((time.monotonic() - started) * 1000)
        result["passed"] = False
    finally:
        if connection is not None:
            connection.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-urls", default="http://127.0.0.1:8081,http://127.0.0.1:8082")
    parser.add_argument("--connections", type=int, default=40)
    parser.add_argument("--timeout-seconds", type=int, default=15)
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
    args = parser.parse_args()

    base_urls = [
        item.strip().rstrip("/")
        for item in args.base_urls.split(",")
        if item.strip()
    ]
    if not base_urls:
        raise SystemExit("At least one base URL is required")

    mysql_args = (
        args.mysql,
        args.mysql_host,
        args.mysql_port,
        args.mysql_user,
        args.mysql_password,
        args.database,
    )
    ticket_no, run_no, run_id = insert_long_running_run(mysql_args)
    try:
        token = login(base_urls[0], args.username, args.password)
        count = max(1, args.connections)
        barrier = threading.Barrier(count + 1)
        results = [dict() for _ in range(count)]
        threads = []
        for index in range(count):
            thread = threading.Thread(
                target=consume_sse,
                args=(
                    base_urls[index % len(base_urls)],
                    run_id,
                    token,
                    args.timeout_seconds,
                    barrier,
                    results[index],
                ),
                daemon=True,
            )
            thread.start()
            threads.append(thread)
        barrier.wait(timeout=10)
        for thread in threads:
            thread.join(timeout=args.timeout_seconds + 5)
        # Give disconnected SSE emitters time to cancel their scheduled pollers
        # before deleting the test run.
        time.sleep(2)

        passed_count = sum(1 for result in results if result.get("passed"))
        failures = [
            {"index": index, **result}
            for index, result in enumerate(results)
            if not result.get("passed")
        ]
        report = {
            "passed": passed_count == count,
            "runId": run_id,
            "runNo": run_no,
            "connections": count,
            "passedConnections": passed_count,
            "failureCount": len(failures),
            "failures": failures[:10],
            "maxElapsedMs": max(
                (result.get("elapsedMs", 0) for result in results),
                default=0,
            ),
        }
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["passed"] else 1
    finally:
        cleanup(mysql_args, ticket_no, run_id)


if __name__ == "__main__":
    raise SystemExit(main())
