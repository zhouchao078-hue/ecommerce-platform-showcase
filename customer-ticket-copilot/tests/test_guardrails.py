import json

from crewai import TaskOutput

from customer_ticket_copilot.guardrails import validate_reply_draft, validate_ticket_analysis


def output(payload: dict) -> TaskOutput:
    return TaskOutput(
        description="test",
        expected_output="json",
        raw=json.dumps(payload, ensure_ascii=False),
        agent="test",
    )


def test_high_risk_analysis_requires_human_review():
    ok, error = validate_ticket_analysis(output({
        "category": "账号安全",
        "risk_level": "high",
        "customer_intent": "账号被盗",
        "confidence": 0.9,
        "requires_human_review": False,
    }))
    assert not ok
    assert "高风险" in error


def test_reply_blocks_unsupported_promise():
    ok, error = validate_reply_draft(output({
        "draft_reply": "我们保证退款。",
        "citations": ["退款政策 v2"],
        "risk_level": "medium",
        "confidence": 0.8,
    }))
    assert not ok
    assert "禁止承诺" in error


def test_reply_accepts_grounded_low_risk_draft():
    ok, value = validate_reply_draft(output({
        "draft_reply": "您好，请提供订单号和设备型号，我们会协助排查。",
        "citations": ["产品支持中心 v1.2"],
        "next_steps": ["补充订单号"],
        "risk_level": "low",
        "confidence": 0.9,
    }))
    assert ok
    assert value.raw
