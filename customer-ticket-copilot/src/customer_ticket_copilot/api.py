import hmac
import os
import threading
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from dotenv import load_dotenv

from .api_models import HealthResponse, TicketAgentResponse, TicketEventRequest
from .service import TicketWorkflow


def _load_environment() -> None:
    module_root = Path(__file__).resolve()
    candidates = (
        Path.cwd() / ".env",
        module_root.parents[3] / ".env",
        module_root.parents[2] / ".env",
    )
    for candidate in candidates:
        if candidate.is_file():
            load_dotenv(candidate, override=False)


_load_environment()


def create_app(
    workflow: TicketWorkflow | None = None,
    internal_token: str | None = None,
) -> FastAPI:
    app = FastAPI(
        title="Customer Ticket Copilot",
        version="1.0.0",
        description="Read-only ticket analysis service. Business writes stay in Java.",
    )
    expected_token = internal_token or os.getenv("INTERNAL_API_TOKEN", "change-me")
    max_concurrent_requests = max(
        1,
        int(os.getenv("TICKET_AGENT_MAX_CONCURRENT_REQUESTS", "4")),
    )
    app.state.workflow = workflow or TicketWorkflow()
    app.state.request_slots = threading.BoundedSemaphore(max_concurrent_requests)

    def require_internal_token(
        x_internal_token: Annotated[
            str | None,
            Header(alias="X-Internal-Token"),
        ] = None,
    ) -> None:
        if not hmac.compare_digest(x_internal_token or "", expected_token):
            raise HTTPException(status_code=403, detail="Invalid internal token")

    @app.get("/health", response_model=HealthResponse)
    def health(request: Request) -> HealthResponse:
        service: TicketWorkflow = request.app.state.workflow
        return HealthResponse(
            status="ok",
            service="customer-ticket-copilot",
            dry_run=service.dry_run,
        )

    @app.post(
        "/api/v1/ticket-events",
        response_model=TicketAgentResponse,
        dependencies=[Depends(require_internal_token)],
    )
    def submit_ticket_event(
        payload: TicketEventRequest,
        request: Request,
    ) -> TicketAgentResponse:
        service: TicketWorkflow = request.app.state.workflow
        slots: threading.BoundedSemaphore = request.app.state.request_slots
        if not slots.acquire(blocking=False):
            raise HTTPException(status_code=429, detail="Ticket agent is busy")
        try:
            return service.submit(payload)
        finally:
            slots.release()

    @app.get(
        "/api/v1/tickets/{ticket_id}",
        response_model=TicketAgentResponse,
        dependencies=[Depends(require_internal_token)],
    )
    def get_ticket(ticket_id: str, request: Request) -> TicketAgentResponse:
        service: TicketWorkflow = request.app.state.workflow
        response = service.get(ticket_id)
        if response is None:
            raise HTTPException(status_code=404, detail="Ticket projection not found")
        return response

    @app.post(
        "/api/v1/agent-runs/{run_id}/cancel",
        dependencies=[Depends(require_internal_token)],
    )
    def cancel_agent_run(run_id: str, request: Request) -> dict[str, bool]:
        service: TicketWorkflow = request.app.state.workflow
        service.cancel(run_id)
        return {"cancelled": True}

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("customer_ticket_copilot.api:app", host="127.0.0.1", port=8000, reload=False)
