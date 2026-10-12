#!/usr/bin/env python3
"""Small dependency-free load smoke test for the Agent chat endpoint."""

import argparse
import json
import statistics
import time
import urllib.error
import urllib.request
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
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read().decode("utf-8", errors="replace")
            return response.status, text, round((time.perf_counter() - started) * 1000)
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        return exc.code, text, round((time.perf_counter() - started) * 1000)
    except Exception as exc:
        return None, str(exc), round((time.perf_counter() - started) * 1000)


def percentile(values, fraction):
    if not values:
        return 0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((len(ordered) - 1) * fraction)))
    return ordered[index]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", default="testuser")
    parser.add_argument("--password", default="change-me")
    parser.add_argument("--users", type=int, default=10)
    parser.add_argument("--requests-per-user", type=int, default=2)
    parser.add_argument("--concurrency", type=int, default=10)
    parser.add_argument("--message", default="推荐一些商品")
    parser.add_argument("--timeout", type=int, default=30)
    args = parser.parse_args()

    login_status, login_text, _ = request_json(
        args.base_url,
        "/api/auth/login",
        method="POST",
        body={"username": args.username, "password": args.password},
        timeout=args.timeout,
    )
    login_payload = json.loads(login_text)
    token = login_payload.get("data", {}).get("accessToken")
    if login_status != 200 or not token:
        raise SystemExit("Login failed: " + login_text[:1000])

    total_requests = max(1, args.users) * max(1, args.requests_per_user)

    def send_one(index):
        status, text, elapsed_ms = request_json(
            args.base_url,
            "/api/chat",
            method="POST",
            body={"message": args.message + f" #{index}" if index else args.message},
            token=token,
            timeout=args.timeout,
        )
        business_code = None
        try:
            business_code = json.loads(text).get("code")
        except Exception:
            pass
        return {
            "httpStatus": status,
            "businessCode": business_code,
            "elapsedMs": elapsed_ms,
        }

    results = []
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as executor:
        futures = [executor.submit(send_one, index) for index in range(total_requests)]
        for future in as_completed(futures):
            results.append(future.result())
    wall_ms = round((time.perf_counter() - started) * 1000)

    status_counts = {}
    latencies = []
    unexpected = []
    for item in results:
        status = item["httpStatus"]
        status_counts[str(status)] = status_counts.get(str(status), 0) + 1
        latencies.append(item["elapsedMs"])
        if status not in (200, 429):
            unexpected.append(item)

    report = {
        "requests": total_requests,
        "concurrency": args.concurrency,
        "wallMs": wall_ms,
        "throughputPerSecond": round(total_requests / max(wall_ms / 1000, 0.001), 2),
        "statusCounts": status_counts,
        "latencyMs": {
            "average": round(statistics.mean(latencies), 2) if latencies else 0,
            "p50": percentile(latencies, 0.50),
            "p95": percentile(latencies, 0.95),
            "p99": percentile(latencies, 0.99),
            "max": max(latencies) if latencies else 0,
        },
        "unexpected": unexpected,
        "passed": not unexpected and status_counts.get("200", 0) > 0,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
