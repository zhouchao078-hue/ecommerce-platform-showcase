import json
import os
import threading
import uuid
from typing import Any

from .api_models import TicketAgentResponse, TicketEventRequest
from .crew import create_crew


HIGH_RISK_TERMS = (
    "refund",
    "chargeback",
    "lawsuit",
    "lawyer",
    "privacy",
    "account stolen",
    "data leak",
    "退款",
    "赔偿",
    "起诉",
    "律师",
    "账号被盗",
    "数据泄露",
)


class TicketWorkflow:
    """Read-only analysis workflow and a temporary response projection store."""

    def __init__(self, dry_run: bool | None = None) -> None:
        self.dry_run = (
            _env_bool("DRY_RUN", True)
            if dry_run is None
            else dry_run
        )
        self._lock = threading.RLock()
        self._projections: dict[str, TicketAgentResponse] = {}
        self._cancelled_runs: set[str] = set()

    def submit(
        self,
        request: TicketEventRequest,
        cancel_event: threading.Event | None = None,
    ) -> TicketAgentResponse:
        ticket_id = request.external_ticket_id or f"TCK-{uuid.uuid4().hex[:16].upper()}"
        trace_id = uuid.uuid4().hex
        if request.run_id and self._is_cancelled(request.run_id, cancel_event):
            return self._cancelled_response(ticket_id, trace_id)
        if cancel_event is not None and cancel_event.is_set():
            return self._cancelled_response(ticket_id, trace_id)

        if self.dry_run or not os.getenv("OPENAI_API_KEY"):
            response = self._fallback_response(request, ticket_id, trace_id)
        else:
            response = self._run_crew(request, ticket_id, trace_id, cancel_event)

        with self._lock:
            self._projections[ticket_id] = response
        return response

    def cancel(self, run_id: str) -> None:
        with self._lock:
            self._cancelled_runs.add(run_id)

    def get(self, ticket_id: str) -> TicketAgentResponse | None:
        with self._lock:
            return self._projections.get(ticket_id)

    def _run_crew(
        self,
        request: TicketEventRequest,
        ticket_id: str,
        trace_id: str,
        cancel_event: threading.Event | None,
    ) -> TicketAgentResponse:
        knowledge = json.dumps(request.business_context, ensure_ascii=False, default=str)
        try:
            if request.run_id and self._is_cancelled(request.run_id, cancel_event):
                return self._cancelled_response(ticket_id, trace_id)
            result = create_crew().kickoff(
                inputs={
                    "ticket": request.content,
                    "knowledge": knowledge,
                }
            )
            if request.run_id and self._is_cancelled(request.run_id, cancel_event):
                return self._cancelled_response(ticket_id, trace_id)
            payload = _parse_json_object(result.raw)
            draft = str(payload.get("draft_reply", "")).strip()
            risk = str(payload.get("risk_level", "medium")).lower()
            human_required = bool(payload.get("requires_human_review", risk in {"high", "critical"}))
            return TicketAgentResponse(
                ticket_id=ticket_id,
                trace_id=trace_id,
                status="accepted",
                category=str(payload.get("category", "general")),
                route="analysis_crew",
                draft_response=draft,
                human_required=human_required,
                human_reason="High-risk request requires human review" if human_required else None,
                classification_confidence=float(payload.get("confidence", 0.5)),
                validation_result={"citations": payload.get("citations", [])},
            )
        except Exception as exc:
            response = self._fallback_response(request, ticket_id, trace_id)
            response.route = "fallback_after_error"
            response.validation_result = {"error": type(exc).__name__}
            return response

    def _is_cancelled(
        self,
        run_id: str,
        cancel_event: threading.Event | None = None,
    ) -> bool:
        if cancel_event is not None and cancel_event.is_set():
            return True
        with self._lock:
            return run_id in self._cancelled_runs

    def _cancelled_response(self, ticket_id: str, trace_id: str) -> TicketAgentResponse:
        return TicketAgentResponse(
            ticket_id=ticket_id,
            trace_id=trace_id,
            status="cancelled",
            category="general",
            route="cancelled",
            draft_response="Generation was cancelled.",
            human_required=False,
            classification_confidence=1.0,
            validation_result={"cancelled": True},
        )

    def _fallback_response(
        self,
        request: TicketEventRequest,
        ticket_id: str,
        trace_id: str,
    ) -> TicketAgentResponse:
        risk = any(term.lower() in request.content.lower() for term in HIGH_RISK_TERMS)
        category = _classify_with_keywords(request.content)
        draft = (
            "Your request has been recorded. Support will review the order and "
            "policy evidence before proposing any action."
        )
        return TicketAgentResponse(
            ticket_id=ticket_id,
            trace_id=trace_id,
            status="accepted",
            category=category,
            route="fallback",
            draft_response=draft,
            human_required=risk,
            human_reason="High-risk request requires human review" if risk else None,
            classification_confidence=0.35,
            validation_result={"citations": ["support-fallback-v1"]},
        )


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _parse_json_object(raw: str) -> dict[str, Any]:
    text = raw.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:].strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("Crew output is not a JSON object")
    value = json.loads(text[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("Crew output must be a JSON object")
    return value


def _classify_with_keywords(content: str) -> str:
    text = content.lower()
    if any(word in text for word in ("refund", "退款", "退货")):
        return "after_sale"
    if any(word in text for word in ("ship", "delivery", "物流", "发货")):
        return "logistics"
    if any(word in text for word in ("order", "订单", "payment", "支付")):
        return "order"
    return "general"
