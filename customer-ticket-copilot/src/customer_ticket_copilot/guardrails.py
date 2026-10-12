"""Deterministic output guardrails for the customer-support workflow."""

import json
import re
from typing import Any

from crewai import TaskOutput


HIGH_RISK_TERMS = (
    "退款",
    "赔偿",
    "起诉",
    "律师",
    "媒体曝光",
    "账号被盗",
    "数据泄露",
    "人身安全",
)

FORBIDDEN_PROMISES = (
    "保证退款",
    "一定赔偿",
    "已经为您处理完成",
    "百分之百",
)

SENSITIVE_PATTERNS = (
    re.compile(r"\b\d{11}\b"),  # phone number
    re.compile(r"\b\d{15,19}\b"),  # bank/card-like number
    re.compile(r"\b\d{17}[0-9Xx]\b"),  # Chinese ID-like number
)


def _enum_value(value: Any) -> str:
    """Normalize both JSON strings and Python Enum values."""
    return str(getattr(value, "value", value)).lower()


def _payload(output: TaskOutput) -> dict[str, Any]:
    if output.pydantic is not None:
        return output.pydantic.model_dump()  # type: ignore[union-attr]
    if output.json_dict is not None:
        return output.json_dict
    value = json.loads(output.raw)
    if not isinstance(value, dict):
        raise ValueError("输出必须是 JSON 对象")
    return value


def validate_ticket_analysis(output: TaskOutput) -> tuple[bool, Any]:
    """Validate the classifier result before it is used downstream."""
    try:
        data = _payload(output)
        required = {"category", "risk_level", "customer_intent", "confidence"}
        missing = sorted(required - data.keys())
        if missing:
            return False, f"缺少字段: {', '.join(missing)}"
        risk = _enum_value(data["risk_level"])
        if risk not in {"low", "medium", "high", "critical"}:
            return False, "risk_level 必须是 low/medium/high/critical"
        if risk in {"high", "critical"} and not data.get("requires_human_review", False):
            return False, "高风险工单必须 requires_human_review=true"
        return True, output
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        return False, f"工单分析输出无效: {exc}"


def validate_reply_draft(output: TaskOutput) -> tuple[bool, Any]:
    """Block unsupported promises, exposed secrets, and missing evidence."""
    try:
        data = _payload(output)
        reply = str(data.get("draft_reply", "")).strip()
        citations = data.get("citations")
        if not reply:
            return False, "draft_reply 不能为空"
        if not isinstance(citations, list) or not citations or any(not str(x).strip() for x in citations):
            return False, "必须提供至少一条非空 citations"
        for phrase in FORBIDDEN_PROMISES:
            if phrase in reply:
                return False, f"回复包含禁止承诺: {phrase}"
        for pattern in SENSITIVE_PATTERNS:
            if pattern.search(reply):
                return False, "回复疑似包含手机号、银行卡号或身份证号"
        if len(reply) > 1000:
            return False, "回复不能超过1000字"
        risk = _enum_value(data.get("risk_level", ""))
        if risk in {"high", "critical"} and not data.get("requires_human_review", False):
            return False, "高风险回复必须转人工审核"
        return True, output
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        return False, f"回复草稿输出无效: {exc}"
