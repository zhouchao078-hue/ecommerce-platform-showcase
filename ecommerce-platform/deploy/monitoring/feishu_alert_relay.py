#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import logging
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


MAX_BODY_BYTES = 1024 * 1024
MAX_MESSAGE_CHARS = 15000
MAX_ALERT_ITEMS = 15

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("feishu-alert-relay")


def env_bool(name, default=False):
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def generate_feishu_sign(timestamp, secret):
    string_to_sign = f"{timestamp}\n{secret}".encode("utf-8")
    digest = hmac.new(string_to_sign, digestmod=hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


def format_alert_message(payload):
    status = str(payload.get("status", "unknown")).lower()
    if status == "firing":
        status_text = "告警触发"
    elif status == "resolved":
        status_text = "告警恢复"
    else:
        status_text = status

    alerts = payload.get("alerts") or []
    lines = [
        f"电商平台 Agent 告警：{status_text}",
        f"当前条目：{len(alerts)} 条",
        f"分组：{payload.get('groupKey', '-')}",
    ]

    mention_all = env_bool("FEISHU_BOT_MENTION_ALL")
    mention_user_ids = [
        item.strip()
        for item in os.getenv("FEISHU_BOT_MENTION_USER_IDS", "").split(",")
        if item.strip()
    ]
    if mention_all:
        lines.insert(0, '<at user_id="all"></at>')
    elif mention_user_ids:
        mentions = " ".join(
            f'<at user_id="{user_id}"></at>' for user_id in mention_user_ids
        )
        lines.insert(0, mentions)

    for index, alert in enumerate(alerts[:MAX_ALERT_ITEMS], start=1):
        labels = alert.get("labels") or {}
        annotations = alert.get("annotations") or {}
        alert_name = labels.get("alertname", "UnknownAlert")
        severity = labels.get("severity", "unknown")
        summary = (
            annotations.get("summary")
            or annotations.get("description")
            or "无告警描述"
        )
        starts_at = alert.get("startsAt", "-")
        lines.extend(
            [
                "",
                f"{index}. {alert_name}  [{severity}]",
                f"{summary}",
                f"开始时间：{starts_at}",
            ]
        )

    if len(alerts) > MAX_ALERT_ITEMS:
        lines.extend(
            [
                "",
                f"其余 {len(alerts) - MAX_ALERT_ITEMS} 条告警请在 Alertmanager 中查看。",
            ]
        )

    message = "\n".join(lines)
    if len(message) > MAX_MESSAGE_CHARS:
        message = message[: MAX_MESSAGE_CHARS - 20] + "\n...(消息已截断)"
    return message


def send_to_feishu(alert_payload):
    webhook_url = os.getenv("FEISHU_BOT_WEBHOOK_URL", "").strip()
    if not webhook_url:
        raise RuntimeError("FEISHU_BOT_WEBHOOK_URL is not configured")

    request_payload = {
        "msg_type": "text",
        "content": {"text": format_alert_message(alert_payload)},
    }
    secret = os.getenv("FEISHU_BOT_SECRET", "").strip()
    if secret:
        timestamp = str(int(time.time()))
        request_payload["timestamp"] = timestamp
        request_payload["sign"] = generate_feishu_sign(timestamp, secret)

    request = Request(
        webhook_url,
        data=json.dumps(request_payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "User-Agent": "ecommerce-alert-relay/1.0",
        },
        method="POST",
    )

    try:
        with urlopen(request, timeout=10) as response:
            response_body = response.read().decode("utf-8", errors="replace")
    except HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Feishu webhook returned HTTP {exc.code}: {error_body[:500]}"
        ) from exc
    except URLError as exc:
        raise RuntimeError(f"Feishu webhook request failed: {exc.reason}") from exc

    try:
        response_json = json.loads(response_body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"Feishu webhook returned invalid JSON: {response_body[:500]}"
        ) from exc

    response_code = response_json.get("code", response_json.get("StatusCode"))
    if response_code not in (None, 0):
        message = response_json.get("msg") or response_json.get("StatusMessage")
        raise RuntimeError(f"Feishu webhook rejected message: {message}")

    return response_body


class AlertRelayHandler(BaseHTTPRequestHandler):
    server_version = "EcommerceAlertRelay/1.0"

    def do_GET(self):
        if self.path != "/health":
            self._write_json(404, {"error": "not found"})
            return

        self._write_json(
            200,
            {
                "status": "ok",
                "feishu_configured": bool(
                    os.getenv("FEISHU_BOT_WEBHOOK_URL", "").strip()
                ),
            },
        )

    def do_POST(self):
        if self.path != "/webhook":
            self._write_json(404, {"error": "not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._write_json(400, {"error": "invalid content length"})
            return

        if content_length <= 0 or content_length > MAX_BODY_BYTES:
            self._write_json(400, {"error": "invalid request body size"})
            return

        try:
            payload = json.loads(self.rfile.read(content_length))
        except json.JSONDecodeError:
            self._write_json(400, {"error": "invalid json"})
            return

        try:
            send_to_feishu(payload)
        except RuntimeError as exc:
            logger.error("Failed to deliver alert to Feishu: %s", exc)
            self._write_json(502, {"error": str(exc)})
            return

        self._write_json(200, {"status": "sent"})

    def _write_json(self, status_code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format_string, *args):
        logger.info("%s - %s", self.address_string(), format_string % args)


def main():
    host = os.getenv("ALERT_RELAY_HOST", "0.0.0.0")
    port = int(os.getenv("ALERT_RELAY_PORT", "8080"))
    server = ThreadingHTTPServer((host, port), AlertRelayHandler)
    server.daemon_threads = True
    logger.info("Feishu alert relay listening on %s:%s", host, port)
    server.serve_forever()


if __name__ == "__main__":
    main()
