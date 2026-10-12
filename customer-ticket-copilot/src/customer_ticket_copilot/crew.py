from crewai import Agent, Crew, Process, Task

from .guardrails import validate_reply_draft, validate_ticket_analysis
from .models import ReplyDraft, TicketAnalysis


def create_crew() -> Crew:
    """Create a read-only analysis and drafting crew.

    No tool capable of sending messages or mutating business data is attached.
    """
    analyst = Agent(
        role="客服工单分析员",
        goal="准确识别客户诉求、风险和需要补充的信息",
        backstory="你只依据输入工单进行分析，不能替客户做退款或赔偿决定。",
        allow_delegation=False,
        verbose=False,
    )
    writer = Agent(
        role="客服回复草稿员",
        goal="根据工单分析和知识库证据生成克制、可执行的回复草稿",
        backstory="你不能编造政策、金额、时限或已执行的操作。",
        allow_delegation=False,
        verbose=False,
    )

    analysis = Task(
        description=(
            "分析以下客服工单：{ticket}\n"
            "输出分类、风险等级、客户意图、缺失信息、是否需要人工审核和置信度。"
        ),
        expected_output="严格符合 TicketAnalysis 的 JSON 对象",
        agent=analyst,
        output_pydantic=TicketAnalysis,
        guardrail=validate_ticket_analysis,
        guardrail_max_retries=2,
    )
    draft = Task(
        description=(
            "根据工单 {ticket}、上一步分析结果和知识库内容 {knowledge} 生成回复草稿。"
            "只使用知识库支持的事实；高风险请求必须转人工。"
        ),
        expected_output="严格符合 ReplyDraft 的 JSON 对象，包含 citations",
        agent=writer,
        context=[analysis],
        output_pydantic=ReplyDraft,
        guardrail=validate_reply_draft,
        guardrail_max_retries=2,
    )
    return Crew(agents=[analyst, writer], tasks=[analysis, draft], process=Process.sequential, verbose=False)
