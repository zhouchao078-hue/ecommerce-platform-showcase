#!/usr/bin/env python3
"""Run a small real-model concurrency and fallback test against /api/chat."""

import argparse
import json
import random
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed


def request_json(base_url, path, method="GET", body=None, token=None, timeout=120):
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
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read().decode("utf-8", errors="replace")
            return {
                "httpStatus": response.status,
                "payload": json.loads(text),
                "latencyMs": int((time.monotonic() - started) * 1000),
            }
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(text)
        except Exception:
            payload = {"raw": text}
        return {
            "httpStatus": exc.code,
            "payload": payload,
            "latencyMs": int((time.monotonic() - started) * 1000),
        }
    except Exception as exc:
        return {
            "httpStatus": None,
            "payload": {"transportError": str(exc)},
            "latencyMs": int((time.monotonic() - started) * 1000),
        }


def register_test_user(base_url, username, password, phone):
    response = request_json(
        base_url,
        "/api/auth/register",
        method="POST",
        body={
            "username": username,
            "password": password,
            "phone": phone,
        },
    )
    token = (response["payload"].get("data") or {}).get("accessToken")
    if response["httpStatus"] != 200 or not token:
        raise RuntimeError(
            "Failed to register "
            + username
            + ": "
            + json.dumps(response["payload"], ensure_ascii=False)
        )
    return token


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


def cleanup_users(mysql_args, username_prefix):
    ids = mysql_exec(
        *mysql_args,
        f"""
        SELECT id FROM sys_user
        WHERE username LIKE '{username_prefix}%';
        """,
    )
    user_ids = [line.strip() for line in ids.splitlines() if line.strip()]
    if not user_ids:
        return
    id_list = ",".join(user_ids)
    mysql_exec(
        *mysql_args,
        f"""
        DELETE FROM chat_message WHERE user_id IN ({id_list});
        DELETE FROM chat_trace WHERE user_id IN ({id_list});
        DELETE FROM user_profile WHERE user_id IN ({id_list});
        DELETE FROM chat_summary
        WHERE session_id IN (
            SELECT CONCAT('user:', id) FROM sys_user WHERE id IN ({id_list})
        );
        DELETE FROM sys_user_role WHERE user_id IN ({id_list});
        DELETE FROM sys_user WHERE id IN ({id_list});
        """,
    )


def run_concurrent_chat(base_url, tokens, message, concurrency, timeout):
    barrier = threading.Barrier(concurrency)
    results = [dict() for _ in range(concurrency)]

    def invoke(index, token):
        try:
            barrier.wait(timeout=15)
            results[index] = request_json(
                base_url,
                "/api/chat",
                method="POST",
                token=token,
                body={"message": message},
                timeout=timeout,
            )
        except Exception as exc:
            results[index] = {
                "httpStatus": None,
                "payload": {"transportError": str(exc)},
                "latencyMs": 0,
            }

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(invoke, index, tokens[index])
            for index in range(concurrency)
        ]
        for future in as_completed(futures):
            future.result()
    return results


def summarize(results):
    successes = [
        item for item in results
        if item.get("httpStatus") == 200
        and (item.get("payload") or {}).get("code") == 200
    ]
    controlled_rejections = [
        item for item in results
        if (item.get("payload") or {}).get("code") == 429
    ]
    server_errors = [
        item for item in results
        if item.get("httpStatus") is None
        or item.get("httpStatus") >= 500
        or (item.get("payload") or {}).get("code") >= 500
    ]
    latencies = sorted(item.get("latencyMs", 0) for item in results)
    return {
        "successCount": len(successes),
        "controlledRejectionCount": len(controlled_rejections),
        "serverErrorCount": len(server_errors),
        "latencyMs": {
            "min": latencies[0] if latencies else 0,
            "p50": latencies[len(latencies) // 2] if latencies else 0,
            "max": latencies[-1] if latencies else 0,
        },
        "responses": results,
    }


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--safe-users", type=int, default=4)
    parser.add_argument("--stress-users", type=int, default=8)
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument(
        "--message",
        default="推荐一款适合新手的相机",
    )
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

    total_users = max(args.safe_users, args.stress_users)
    batch = uuid.uuid4().hex[:8]
    prefix = f"llmtest_{batch}_"
    mysql_args = (
        args.mysql,
        args.mysql_host,
        args.mysql_port,
        args.mysql_user,
        args.mysql_password,
        args.database,
    )
    tokens = []
    try:
        for index in range(total_users):
            phone = "139" + str(
                (int(time.time() * 1000) + random.randint(0, 999999) + index)
                % 100000000
            ).zfill(8)
            tokens.append(
                register_test_user(
                    args.base_url,
                    f"{prefix}{index}",
                    args.password,
                    phone,
                )
            )

        safe_results = run_concurrent_chat(
            args.base_url,
            tokens[: args.safe_users],
            args.message,
            args.safe_users,
            args.timeout_seconds,
        )
        stress_results = run_concurrent_chat(
            args.base_url,
            tokens[: args.stress_users],
            args.message,
            args.stress_users,
            args.timeout_seconds,
        )
        safe_summary = summarize(safe_results)
        stress_summary = summarize(stress_results)
        report = {
            "passed": (
                safe_summary["successCount"] == args.safe_users
                and safe_summary["serverErrorCount"] == 0
                and stress_summary["serverErrorCount"] == 0
                and (
                    stress_summary["successCount"]
                    + stress_summary["controlledRejectionCount"]
                    == args.stress_users
                )
            ),
            "batch": batch,
            "safePhase": safe_summary,
            "stressPhase": stress_summary,
        }
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0 if report["passed"] else 1
    finally:
        cleanup_users(mysql_args, prefix)


if __name__ == "__main__":
    raise SystemExit(main())
