from fastapi.testclient import TestClient

from customer_ticket_copilot.api import create_app
from customer_ticket_copilot.service import TicketWorkflow


def client() -> TestClient:
    return TestClient(
        create_app(TicketWorkflow(dry_run=True), internal_token="test-token"),
        headers={"X-Internal-Token": "test-token"},
    )


def test_health() -> None:
    response = client().get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["dry_run"] is True


def test_business_endpoint_rejects_missing_internal_token() -> None:
    api = TestClient(create_app(TicketWorkflow(dry_run=True), internal_token="test-token"))
    response = api.post(
        "/api/v1/ticket-events",
        json={
            "tenant_id": "ecommerce",
            "channel": "ecommerce-platform",
            "external_ticket_id": "SP-AUTH-1",
            "customer_id": "1",
            "subject": "Auth test",
            "content": "This request must be rejected.",
        },
    )
    assert response.status_code == 403


def test_submit_and_get_ticket_projection() -> None:
    api = client()
    submitted = api.post(
        "/api/v1/ticket-events",
        json={
            "tenant_id": "ecommerce",
            "channel": "ecommerce-platform",
            "external_ticket_id": "SP-TEST-1",
            "external_message_id": "MSG-1",
            "customer_id": "1",
            "subject": "Delivery delay",
            "content": "My order has not shipped for five days.",
            "business_context": {"order_id": "ORD-1", "status": "paid"},
        },
    )
    assert submitted.status_code == 200
    payload = submitted.json()
    assert payload["ticket_id"] == "SP-TEST-1"
    assert payload["route"] == "fallback"
    assert payload["status"] == "accepted"

    fetched = api.get("/api/v1/tickets/SP-TEST-1")
    assert fetched.status_code == 200
    assert fetched.json()["trace_id"] == payload["trace_id"]


def test_cancel_marks_run_as_cancelled() -> None:
    api = client()
    cancelled = api.post("/api/v1/agent-runs/RUN-CANCEL-1/cancel")
    assert cancelled.status_code == 200
    assert cancelled.json()["cancelled"] is True

    submitted = api.post(
        "/api/v1/ticket-events",
        json={
            "run_id": "RUN-CANCEL-1",
            "tenant_id": "ecommerce",
            "channel": "ecommerce-platform",
            "external_ticket_id": "SP-CANCEL-1",
            "customer_id": "1",
            "subject": "Cancellation test",
            "content": "Cancel this generation.",
        },
    )
    assert submitted.status_code == 200
    assert submitted.json()["status"] == "cancelled"
