from enum import Enum

from pydantic import BaseModel, Field


class RiskLevel(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"


class TicketAnalysis(BaseModel):
    category: str = Field(min_length=1, max_length=80)
    risk_level: RiskLevel
    customer_intent: str = Field(min_length=1, max_length=500)
    missing_information: list[str] = Field(default_factory=list)
    requires_human_review: bool = False
    confidence: float = Field(ge=0, le=1)


class ReplyDraft(BaseModel):
    draft_reply: str = Field(min_length=1, max_length=1000)
    citations: list[str] = Field(min_length=1)
    next_steps: list[str] = Field(default_factory=list)
    risk_level: RiskLevel
    requires_human_review: bool = False
    confidence: float = Field(ge=0, le=1)
