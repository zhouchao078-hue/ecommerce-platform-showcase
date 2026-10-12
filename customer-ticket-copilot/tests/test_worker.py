import json
import threading
from types import SimpleNamespace
from unittest.mock import Mock

import httpx

from customer_ticket_copilot import worker
from customer_ticket_copilot.service import TicketWorkflow


def test_worker_acks_without_callback_when_result_was_already_persisted(monkeypatch):
    channel = Mock()
    method = SimpleNamespace(delivery_tag=7)
    body = json.dumps(
        {
            "runId": 11,
            "ticketNo": "SP-TEST-1",
            "requestPayload": {
                "tenant_id": "ecommerce",
                "channel": "ecommerce-platform",
                "external_ticket_id": "SP-TEST-1",
                "customer_id": "1",
                "subject": "Delivery delay",
                "content": "Where is my order?",
            },
        }
    ).encode()

    monkeypatch.setattr(worker, "start_lease", lambda *args, **kwargs: None)
    monkeypatch.setattr(
        worker,
        "fetch_checkpoint",
        lambda *args, **kwargs: {"lastCompletedStep": "RESULT_PERSISTED"},
    )
    callback_success = Mock()
    monkeypatch.setattr(worker, "callback_success", callback_success)

    worker.handle_message(
        channel,
        method,
        body,
        workflow=TicketWorkflow(dry_run=True),
        api_base_url="http://127.0.0.1:8081",
        internal_token="test-token",
        worker_id="worker-1",
        active_runs={},
    )

    channel.basic_ack.assert_called_once_with(delivery_tag=7)
    callback_success.assert_not_called()


def test_cancel_message_marks_local_active_run():
    channel = Mock()
    method = SimpleNamespace(delivery_tag=8)
    cancel_event = threading.Event()
    active_runs = {"11": cancel_event}

    worker.handle_cancel(
        channel,
        method,
        json.dumps({"runId": 11}).encode(),
        workflow=TicketWorkflow(dry_run=True),
        active_runs=active_runs,
    )

    assert cancel_event.is_set()
    channel.basic_ack.assert_called_once_with(delivery_tag=8)


def test_worker_acks_duplicate_delivery_when_lease_is_rejected(monkeypatch):
    channel = Mock()
    method = SimpleNamespace(delivery_tag=9)
    body = json.dumps(
        {
            "runId": 12,
            "ticketNo": "SP-TEST-2",
            "requestPayload": {
                "tenant_id": "ecommerce",
                "channel": "ecommerce-platform",
                "external_ticket_id": "SP-TEST-2",
                "customer_id": "1",
                "subject": "Cancelled run",
                "content": "This run was cancelled.",
            },
        }
    ).encode()
    request = httpx.Request("POST", "http://127.0.0.1:8081/api/internal/ticket-agent/runs/12/start")
    response = httpx.Response(409, request=request)

    def reject_lease(*args, **kwargs):
        raise httpx.HTTPStatusError("conflict", request=request, response=response)

    callback_success = Mock()
    monkeypatch.setattr(worker, "start_lease", reject_lease)
    monkeypatch.setattr(worker, "callback_success", callback_success)

    worker.handle_message(
        channel,
        method,
        body,
        workflow=TicketWorkflow(dry_run=True),
        api_base_url="http://127.0.0.1:8081",
        internal_token="test-token",
        worker_id="worker-1",
        active_runs={},
    )

    channel.basic_ack.assert_called_once_with(delivery_tag=9)
    callback_success.assert_not_called()
