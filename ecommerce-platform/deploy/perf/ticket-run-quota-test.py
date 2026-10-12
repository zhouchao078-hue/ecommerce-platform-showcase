#!/usr/bin/env python3
"""Verify distributed per-user active AgentRun quota behavior."""

import argparse
import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed


def request_json(base_url, path, method="GET", body=None, token=None, timeout=30):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token

    request = urllib.request.Request(
        base_url + path,
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", default="testuser")
    parser.add_argument("--password", default="change-me")
    parser.add_argument("--burst", type=int, default=2)
    args = parser.parse_args()

    login_status, login_payload = request_json(
        args.base_url,
        "/api/auth/login",
        method="POST",
        body={"username": args.username, "password": args.password},
    )
    token = login_payload.get("data", {}).get("accessToken")
    if login_status != 200 or not token:
        raise SystemExit("Login failed: " + json.dumps(login_payload, ensure_ascii=False))

    def submit(index):
        request_id = "quota-test-" + uuid.uuid4().hex
        status, payload = request_json(
            args.base_url,
            "/api/ticket-agent/tickets",
            method="POST",
            token=token,
            body={
                "externalTicketId": request_id,
                "externalMessageId": request_id,
                "subject": f"quota test {index}",
                "content": f"quota test request {index}",
            },
        )
        return {"index": index, "httpStatus": status, "payload": payload}

    burst_results = []
    with ThreadPoolExecutor(max_workers=max(1, args.burst)) as executor:
        futures = [executor.submit(submit, index) for index in range(args.burst)]
        for future in as_completed(futures):
            burst_results.append(future.result())

    successful = [
        item for item in burst_results
        if item["httpStatus"] == 200
        and isinstance(item["payload"].get("data"), dict)
        and item["payload"]["data"].get("runId") is not None
    ]
    throttled = [
        item for item in burst_results
        if item["httpStatus"] == 429 or item["payload"].get("code") == 429
    ]

    if len(successful) != 1:
        print(json.dumps({
            "passed": False,
            "reason": "Expected exactly one successful submission",
            "burstResults": burst_results,
        }, ensure_ascii=False, indent=2))
        return 1

    run_id = successful[0]["payload"]["data"]["runId"]
    cancel_status, cancel_payload = request_json(
        args.base_url,
        f"/api/ticket-agent/runs/{run_id}/cancel",
        method="POST",
        token=token,
    )

    terminal_status = None
    for _ in range(20):
        status, payload = request_json(
            args.base_url,
            f"/api/ticket-agent/runs/{run_id}",
            token=token,
        )
        terminal_status = payload.get("data", {}).get("status")
        if status == 200 and terminal_status == "CANCELLED":
            break
        time.sleep(0.25)

    after_cancel_status, after_cancel_payload = request_json(
        args.base_url,
        "/api/ticket-agent/tickets",
        method="POST",
        token=token,
        body={
            "externalTicketId": "quota-after-cancel-" + uuid.uuid4().hex,
            "externalMessageId": "quota-after-cancel-" + uuid.uuid4().hex,
            "subject": "quota after cancel",
            "content": "quota after cancel",
        },
    )
    after_cancel_ok = (
        after_cancel_status == 200
        and isinstance(after_cancel_payload.get("data"), dict)
        and after_cancel_payload["data"].get("runId") is not None
    )

    report = {
        "passed": (
            len(successful) == 1
            and len(throttled) == args.burst - 1
            and cancel_status == 200
            and terminal_status == "CANCELLED"
            and after_cancel_ok
        ),
        "burst": args.burst,
        "burstResults": burst_results,
        "successfulRunId": run_id,
        "cancelHttpStatus": cancel_status,
        "terminalStatus": terminal_status,
        "afterCancelHttpStatus": after_cancel_status,
        "afterCancelRunId": after_cancel_payload.get("data", {}).get("runId")
        if isinstance(after_cancel_payload.get("data"), dict) else None,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
