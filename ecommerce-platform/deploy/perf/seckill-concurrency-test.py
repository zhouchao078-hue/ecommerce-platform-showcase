#!/usr/bin/env python3
import argparse
import concurrent.futures
import json
import time
from datetime import datetime, timedelta
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def parse_args():
    parser = argparse.ArgumentParser(description="Concurrent seckill purchase test")
    parser.add_argument("--base-url", default="http://localhost:8080/api")
    parser.add_argument("--merchant-username", default="testuser")
    parser.add_argument("--merchant-password", default="change-me")
    parser.add_argument("--product-id", type=int, required=True)
    parser.add_argument("--users", type=int, default=25)
    parser.add_argument("--stock", type=int, default=10)
    parser.add_argument("--workers", type=int, default=25)
    return parser.parse_args()


def request_json(method, url, body=None, token=None, timeout=20):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(
        url,
        data=None if body is None else json.dumps(body).encode("utf-8"),
        headers=headers,
        method=method,
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {"message": raw}
        return exc.code, payload
    except URLError as exc:
        return 0, {"message": str(exc.reason)}


def login(base_url, username, password):
    status, payload = request_json(
        "POST",
        f"{base_url}/auth/login",
        {"username": username, "password": password},
    )
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"merchant login failed: status={status}, body={payload}")
    return payload["data"]["accessToken"]


def register_users(base_url, count):
    suffix = int(time.time() * 1000)
    tokens = []
    for index in range(count):
        username = f"skload{suffix}{index:02d}"
        phone = f"139{str(suffix)[-6:]}{index:02d}"
        status, payload = request_json(
            "POST",
            f"{base_url}/auth/register",
            {"username": username, "password": "Test123456", "phone": phone},
        )
        if status != 200 or payload.get("code") != 200:
            raise RuntimeError(
                f"register failed for {username}: status={status}, body={payload}"
            )
        tokens.append(payload["data"]["accessToken"])
    return tokens


def create_seckill(base_url, merchant_token, product_id, stock):
    now = datetime.now()
    status, payload = request_json(
        "POST",
        f"{base_url}/seckill/products",
        {
            "productId": product_id,
            "seckillPrice": 0.01,
            "stock": stock,
            "startTime": (now - timedelta(minutes=1)).strftime("%Y-%m-%d %H:%M:%S"),
            "endTime": (now + timedelta(minutes=10)).strftime("%Y-%m-%d %H:%M:%S"),
        },
        token=merchant_token,
    )
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"create seckill failed: status={status}, body={payload}")

    status, payload = request_json("GET", f"{base_url}/seckill/products")
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"list seckill failed: status={status}, body={payload}")
    candidates = [
        item
        for item in payload.get("data", [])
        if item.get("productId") == product_id and item.get("stock") == stock
    ]
    if not candidates:
        raise RuntimeError("created seckill activity was not found")
    return max(candidates, key=lambda item: item["id"])["id"]


def purchase(base_url, token, seckill_id, index):
    status, payload = request_json(
        "POST",
        f"{base_url}/seckill/{seckill_id}/purchase",
        token=token,
    )
    if status == 200 and payload.get("code") == 200:
        return index, "success", payload["data"]["orderNo"]
    return index, "rejected", payload.get("message", f"status={status}")


def main():
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    merchant_token = login(
        base_url, args.merchant_username, args.merchant_password)
    user_tokens = register_users(base_url, args.users)
    seckill_id = create_seckill(
        base_url, merchant_token, args.product_id, args.stock)

    started = time.perf_counter()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [
            executor.submit(purchase, base_url, token, seckill_id, index)
            for index, token in enumerate(user_tokens)
        ]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    elapsed_ms = round((time.perf_counter() - started) * 1000)

    successes = [item for item in results if item[1] == "success"]
    rejected = [item for item in results if item[1] == "rejected"]
    expected_successes = min(args.users, args.stock)
    duplicate_order_consistent = None
    if successes:
        success_index, _, first_order_no = successes[0]
        duplicate_index, duplicate_status, duplicate_order_no = purchase(
            base_url,
            user_tokens[success_index],
            seckill_id,
            success_index,
        )
        duplicate_order_consistent = (
            duplicate_index == success_index
            and duplicate_status == "success"
            and duplicate_order_no == first_order_no
        )

    status, detail = request_json(
        "GET", f"{base_url}/seckill/products/{seckill_id}")
    remaining_stock = (
        detail.get("data", {}).get("stock") if status == 200 else None
    )

    summary = {
        "seckillId": seckill_id,
        "users": args.users,
        "initialStock": args.stock,
        "expectedSuccesses": expected_successes,
        "successes": len(successes),
        "rejected": len(rejected),
        "remainingStock": remaining_stock,
        "elapsedMs": elapsed_ms,
        "duplicateOrderConsistent": duplicate_order_consistent,
        "passed": (
            len(successes) == expected_successes
            and remaining_stock == max(0, args.stock - args.users)
            and duplicate_order_consistent is True
        ),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if not summary["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
