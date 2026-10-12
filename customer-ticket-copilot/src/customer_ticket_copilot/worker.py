import json
import os
import socket
import threading
import time
import uuid
from typing import Any

import httpx
import pika

from .api_models import TicketAgentResponse, TicketEventRequest
from .service import TicketWorkflow


def main() -> None:
    rabbit_url = os.getenv(
        "RABBITMQ_URL",
        "amqp://change-me:change-me@localhost:5672/%2F",
    )
    queues = [
        queue.strip()
        for queue in os.getenv(
            "TICKET_AGENT_QUEUES",
            "support.agent.run.high,support.agent.run.normal,support.agent.run.low",
        ).split(",")
        if queue.strip()
    ]
    cancel_queue = os.getenv("TICKET_AGENT_CANCEL_QUEUE", "support.agent.cancel")
    api_base_url = os.getenv("ECOMMERCE_API_URL", "http://127.0.0.1:8081")
    internal_token = os.getenv("INTERNAL_API_TOKEN", "change-me")
    prefetch = int(os.getenv("TICKET_AGENT_PREFETCH", "1"))
    worker_id = os.getenv(
        "TICKET_AGENT_WORKER_ID",
        f"{socket.gethostname()}-{os.getpid()}-{uuid.uuid4().hex[:6]}",
    )

    workflow = TicketWorkflow()
    active_runs: dict[str, threading.Event] = {}
    connection = pika.BlockingConnection(pika.URLParameters(rabbit_url))
    channel = connection.channel()
    channel.basic_qos(prefetch_count=max(1, prefetch))
    for queue in queues:
        channel.queue_declare(queue=queue, durable=True)
        channel.basic_consume(
            queue=queue,
            on_message_callback=lambda ch, method, properties, body: handle_message(
                ch,
                method,
                body,
                workflow=workflow,
                api_base_url=api_base_url,
                internal_token=internal_token,
                worker_id=worker_id,
                active_runs=active_runs,
            ),
            auto_ack=False,
        )
    channel.queue_declare(queue=cancel_queue, durable=True)
    channel.basic_consume(
        queue=cancel_queue,
        on_message_callback=lambda ch, method, properties, body: handle_cancel(
            ch,
            method,
            body,
            workflow=workflow,
            active_runs=active_runs,
        ),
        auto_ack=False,
    )
    channel.start_consuming()


def handle_message(
    channel: Any,
    method: Any,
    body: bytes,
    *,
    workflow: TicketWorkflow,
    api_base_url: str,
    internal_token: str,
    worker_id: str,
    active_runs: dict[str, threading.Event],
) -> None:
    started_at = time.monotonic()
    heartbeat_stop = threading.Event()
    active_key: str | None = None
    try:
        envelope = json.loads(body.decode("utf-8"))
        request_payload = envelope["requestPayload"]
        if isinstance(request_payload, str):
            request_payload = json.loads(request_payload)
        run_id = envelope["runId"]
        ticket_no = envelope["ticketNo"]
        active_key = str(run_id)
        cancel_event = threading.Event()
        active_runs[active_key] = cancel_event
        request_payload["run_id"] = str(run_id)
        request = TicketEventRequest.model_validate(request_payload)
        start_lease(api_base_url, internal_token, run_id, worker_id)
        heartbeat_thread = threading.Thread(
            target=heartbeat_loop,
            args=(
                api_base_url,
                internal_token,
                run_id,
                worker_id,
                heartbeat_stop,
                cancel_event,
            ),
            daemon=True,
        )
        heartbeat_thread.start()

        checkpoint = fetch_checkpoint(api_base_url, internal_token, run_id)
        if checkpoint.get("lastCompletedStep") == "RESULT_PERSISTED":
            # The result transaction already committed. A redelivery is at-least-once,
            # so acknowledge it without invoking CrewAI or writing another Agent message.
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
        if cancel_event.is_set():
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
        model_payload = checkpoint.get("modelResponsePayload")
        if model_payload:
            result = TicketAgentResponse.model_validate_json(model_payload)
        else:
            result = workflow.submit(request, cancel_event=cancel_event)
        if cancel_event.is_set() or result.status == "cancelled":
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return
        callback_success(
            api_base_url,
            internal_token,
            run_id,
            ticket_no,
            result.model_dump(),
            int((time.monotonic() - started_at) * 1000),
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except httpx.HTTPStatusError as exc:
        if exc.response.status_code == 409:
            # Another worker already owns this run. Ack the duplicate delivery.
            channel.basic_ack(delivery_tag=method.delivery_tag)
        else:
            handle_worker_failure(
                channel,
                method,
                api_base_url,
                internal_token,
                locals().get("run_id"),
                str(exc),
                int((time.monotonic() - started_at) * 1000),
            )
    except Exception as exc:
        handle_worker_failure(
            channel,
            method,
            api_base_url,
            internal_token,
            locals().get("run_id"),
            str(exc),
            int((time.monotonic() - started_at) * 1000),
        )
    finally:
        heartbeat_stop.set()
        if active_key is not None:
            active_runs.pop(active_key, None)


def handle_cancel(
    channel: Any,
    method: Any,
    body: bytes,
    *,
    workflow: TicketWorkflow,
    active_runs: dict[str, threading.Event],
) -> None:
    try:
        envelope = json.loads(body.decode("utf-8"))
        run_id = str(envelope["runId"])
        workflow.cancel(run_id)
        cancel_event = active_runs.get(run_id)
        if cancel_event is not None:
            cancel_event.set()
    finally:
        # Cancellation is best-effort; the DB heartbeat remains the source of truth.
        channel.basic_ack(delivery_tag=method.delivery_tag)


def handle_worker_failure(
    channel: Any,
    method: Any,
    api_base_url: str,
    internal_token: str,
    run_id: int | None,
    error_message: str,
    duration_ms: int,
) -> None:
    try:
        callback_failure(
            api_base_url,
            internal_token,
            run_id,
            error_message,
            duration_ms,
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception:
        # Leave the message unacked so RabbitMQ can redeliver it.
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)


def start_lease(
    api_base_url: str,
    internal_token: str,
    run_id: int,
    worker_id: str,
) -> None:
    response = httpx.post(
        f"{api_base_url}/api/internal/ticket-agent/runs/{run_id}/start",
        headers={"X-Internal-Token": internal_token},
        json={"workerId": worker_id, "leaseSeconds": 60},
        timeout=10,
    )
    response.raise_for_status()


def heartbeat_loop(
    api_base_url: str,
    internal_token: str,
    run_id: int,
    worker_id: str,
    stop_event: threading.Event,
    cancel_event: threading.Event,
) -> None:
    while not stop_event.wait(20):
        try:
            response = httpx.post(
                f"{api_base_url}/api/internal/ticket-agent/runs/{run_id}/heartbeat",
                headers={"X-Internal-Token": internal_token},
                json={"workerId": worker_id, "leaseSeconds": 60},
                timeout=10,
            )
            response.raise_for_status()
            payload = response.json().get("data") or {}
            if payload.get("status") in {"CANCEL_REQUESTED", "CANCELLED"}:
                cancel_event.set()
                return
        except Exception:
            # Main worker path will report the terminal failure if the lease is lost.
            return


def fetch_checkpoint(
    api_base_url: str,
    internal_token: str,
    run_id: int,
) -> dict[str, Any]:
    response = httpx.get(
        f"{api_base_url}/api/internal/ticket-agent/runs/{run_id}/checkpoint",
        headers={"X-Internal-Token": internal_token},
        timeout=10,
    )
    response.raise_for_status()
    payload = response.json()
    return payload.get("data") or {}


def callback_success(
    api_base_url: str,
    internal_token: str,
    run_id: int,
    ticket_no: str,
    result: dict[str, Any],
    duration_ms: int,
) -> None:
    response = httpx.post(
        f"{api_base_url}/api/internal/ticket-agent/runs/{run_id}/result",
        headers={"X-Internal-Token": internal_token},
        json={
            "ticketNo": ticket_no,
            "result": result,
            "durationMs": duration_ms,
        },
        timeout=15,
    )
    response.raise_for_status()


def callback_failure(
    api_base_url: str,
    internal_token: str,
    run_id: int | None,
    error_message: str,
    duration_ms: int,
) -> None:
    if run_id is None:
        raise ValueError("runId is missing from worker message")
    response = httpx.post(
        f"{api_base_url}/api/internal/ticket-agent/runs/{run_id}/failure",
        headers={"X-Internal-Token": internal_token},
        json={
            "errorMessage": error_message,
            "durationMs": duration_ms,
        },
        timeout=15,
    )
    response.raise_for_status()


if __name__ == "__main__":
    main()
