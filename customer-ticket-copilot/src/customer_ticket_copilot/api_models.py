from typing import Any

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    service: str
    dry_run: bool


class TicketEventRequest(BaseModel):
    run_id: str | None = Field(default=None, max_length=64)
    tenant_id: str = Field(min_length=1, max_length=64)
    channel: str = Field(min_length=1, max_length=32)
    event_id: str | None = Field(default=None, max_length=64)
    external_ticket_id: str | None = Field(default=None, max_length=64)
    external_message_id: str | None = Field(default=None, max_length=64)
    customer_id: str = Field(min_length=1, max_length=64)
    subject: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1, max_length=5000)
    business_context: dict[str, Any] = Field(default_factory=dict)


class TicketAgentResponse(BaseModel):
    ticket_id: str
    trace_id: str
    status: str
    category: str
    route: str
    draft_response: str
    final_response: str | None = None
    human_required: bool
    human_reason: str | None = None
    review_id: str | None = None
    classification_confidence: float = Field(ge=0, le=1)
    validation_result: dict[str, Any] | list[Any] | str | None = None
