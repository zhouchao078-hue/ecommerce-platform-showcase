#!/usr/bin/env python3
"""Verify AgentRun quota, cancellation, and idempotent callbacks across two instances."""

import argparse
import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed


def request_json(base_url, path, method="GET", body=None, token=None,
                 internal_token=None, timeout=30):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    if internal_token:
        headers["X-Internal-Token"] = internal_token

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
    parser.add_argument("--base-urls", default="http://127.0.0.1:8081,http://127.0.0.1:8082")
    parser.add_argument("--username", default="testuser")
    parser.add_argument("--password", default="change-me")
    parser.add_argument("--internal-token", default="change-me")
    args = parser.parse_args()
    base_urls = [item.strip().rstrip("/") for item in args.base_urls.split(",") if item.strip()]
    if len(base_urls) != 2:
        raise SystemExit("Exactly two base URLs are required")

    login_status, login_payload = request_json(
        base_urls[0],
        "/api/auth/login",
        method="POST",
        body={"username": args.username, "password": args.password},
    )
    token = login_payload.get("data", {}).get("accessToken")
    if login_status != 200 or not token:
        raise SystemExit("Login failed: " + json.dumps(login_payload, ensure_ascii=False))

    def submit(base_url, label):
        external_id = f"multi-instance-{label}-{uuid.uuid4().hex}"
        status, payload = request_json(
            base_url,
            "/api/ticket-agent/tickets",
            method="POST",
            token=token,
            body={
                "externalTicketId": external_id,
                "externalMessageId": external_id,
                "subject": f"multi instance {label}",
                "content": f"multi instance request {label}",
            },
        )
        return {"baseUrl": base_url, "httpStatus": status, "payload": payload}

    def submit_pair():
        results = []
        with ThreadPoolExecutor(max_workers=2) as executor:
            futures = [
                executor.submit(submit, base_urls[0], "a"),
                executor.submit(submit, base_urls[1], "b"),
            ]
            for future in as_completed(futures):
                results.append(future.result())
        return results

    first_pair = submit_pair()
    first_successes = [
        item for item in first_pair
        if item["httpStatus"] == 200
        and isinstance(item["payload"].get("data"), dict)
        and item["payload"]["data"].get("runId") is not None
    ]
    first_rejections = [
        item for item in first_pair
        if item["httpStatus"] == 429 or item["payload"].get("code") == 429
    ]
    if len(first_successes) != 1 or len(first_rejections) != 1:
        print(json.dumps({
            "passed": False,
            "reason": "Cross-instance quota did not allow exactly one run",
            "firstPair": first_pair,
        }, ensure_ascii=False, indent=2))
        return 1

    first_run = first_successes[0]["payload"]["data"]
    cancel_base = (
        base_urls[1]
        if first_successes[0]["baseUrl"] == base_urls[0]
        else base_urls[0]
    )
    cancel_status, cancel_payload = request_json(
        cancel_base,
        f"/api/ticket-agent/runs/{first_run['runId']}/cancel",
        method="POST",
        token=token,
    )

    first_terminal_status = None
    for _ in range(20):
        status, payload = request_json(
            base_urls[0],
            f"/api/ticket-agent/runs/{first_run['runId']}",
            token=token,
        )
        first_terminal_status = payload.get("data", {}).get("status")
        if status == 200 and first_terminal_status == "CANCELLED":
            break
        time.sleep(0.25)

    second_pair = submit_pair()
    second_successes = [
        item for item in second_pair
        if item["httpStatus"] == 200
        and isinstance(item["payload"].get("data"), dict)
        and item["payload"]["data"].get("runId") is not None
    ]
    second_rejections = [
        item for item in second_pair
        if item["httpStatus"] == 429 or item["payload"].get("code") == 429
    ]
    if len(second_successes) != 1 or len(second_rejections) != 1:
        print(json.dumps({
            "passed": False,
            "reason": "Quota did not recover after cross-instance cancellation",
            "secondPair": second_pair,
        }, ensure_ascii=False, indent=2))
        return 1

    run = second_successes[0]["payload"]["data"]
    lease_status, lease_payload = request_json(
        base_urls[1],
        f"/api/internal/ticket-agent/runs/{run['runId']}/start",
        method="POST",
        internal_token=args.internal_token,
        body={"workerId": "multi-instance-worker", "leaseSeconds": 60},
    )
    if lease_status != 200 or lease_payload.get("data", {}).get("status") != "RUNNING":
        print(json.dumps({
            "passed": False,
            "reason": "Failed to start lease",
            "leaseStatus": lease_status,
            "leasePayload": lease_payload,
        }, ensure_ascii=False, indent=2))
        return 1

    marker = "idempotent-response-" + uuid.uuid4().hex
    callback_body = {
        "ticketNo": run["ticketNo"],
        "result": {
            "ticket_id": f"external-{run['runNo']}",
            "trace_id": "trace-" + uuid.uuid4().hex,
            "status": "completed",
            "final_response": marker,
            "human_required": False,
            "validation_result": {"ok": True},
        },
        "durationMs": 123,
    }

    def callback(base_url):
        status, payload = request_json(
            base_url,
            f"/api/internal/ticket-agent/runs/{run['runId']}/result",
            method="POST",
            internal_token=args.internal_token,
            body=callback_body,
        )
        return {"baseUrl": base_url, "httpStatus": status, "payload": payload}

    callbacks = []
    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(callback, base_url) for base_url in base_urls]
        for future in as_completed(futures):
            callbacks.append(future.result())

    terminal_status = None
    for _ in range(20):
        status, payload = request_json(
            base_urls[0],
            f"/api/ticket-agent/runs/{run['runId']}",
            token=token,
        )
        terminal_status = payload.get("data", {}).get("status")
        if status == 200 and terminal_status == "SUCCEEDED":
            break
        time.sleep(0.25)

    detail_status, detail_payload = request_json(
        base_urls[0],
        f"/api/support/tickets/{run['ticketNo']}",
        token=token,
    )
    messages = detail_payload.get("data", {}).get("messages", [])
    matching_messages = [
        message for message in messages
        if message.get("senderType") == "AGENT" and message.get("content") == marker
    ]

    report = {
        "passed": (
            len(first_successes) == 1
            and len(first_rejections) == 1
            and cancel_status == 200
            and first_terminal_status == "CANCELLED"
            and len(second_successes) == 1
            and len(second_rejections) == 1
            and lease_status == 200
            and all(item["httpStatus"] == 200 for item in callbacks)
            and terminal_status == "SUCCEEDED"
            and len(matching_messages) == 1
        ),
        "firstPair": first_pair,
        "crossInstanceCancelHttpStatus": cancel_status,
        "firstTerminalStatus": first_terminal_status,
        "secondPair": second_pair,
        "leaseHttpStatus": lease_status,
        "duplicateCallbacks": callbacks,
        "terminalStatus": terminal_status,
        "matchingAgentMessages": len(matching_messages),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
