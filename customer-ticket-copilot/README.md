# Customer Ticket Copilot

基于 CrewAI 的企业客服工单分析与回复辅助项目骨架。

当前版本只生成“带依据的回复草稿”，不会自动发送、退款、修改订单或变更客户资料。高风险工单必须人工确认。

## 运行

```powershell
cd path\to\ecommerce-project\customer-ticket-copilot
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .
Copy-Item .env.example .env
python -m customer_ticket_copilot.main
```

如果直接使用本地源码开发版 CrewAI，建议在项目虚拟环境中安装核心包：

```powershell
pip install -e E:\xiangmu\crewAI\lib\crewai-core `
            -e E:\xiangmu\crewAI\lib\crewai
```

`crewai-files` 仅在启用文件处理扩展时需要，本项目当前不依赖它。

需要配置 `OPENAI_API_KEY` 或 CrewAI 支持的其他模型环境变量。项目不会把密钥写入 Agent prompt、工单内容或日志。

## 设计边界

- `output_pydantic`：约束任务之间传递的数据结构。
- 程序型 guardrail：检查字段、敏感信息、禁止承诺和引用。
- LLM guardrail：可选，用于语义质量检查；不能替代权限控制。
- 高风险类别（退款、赔偿、账号安全、法律投诉等）强制 `requires_human_review=true`。
- 真实系统接入时，应在 API/工具网关外部执行 SSO、RBAC、脱敏、审计和人工审批。

## 测试

```powershell
pytest -q
```

## HTTP service

The Java commerce platform calls this service through the contract in
`path\to\ecommerce-project\contracts\ticket-agent.openapi.yaml`.

```powershell
.\.venv\Scripts\python.exe -m uvicorn customer_ticket_copilot.api:app --host 127.0.0.1 --port 8000
```

Business endpoints require the shared `X-Internal-Token` header:

- `GET /health`
- `POST /api/v1/ticket-events`
- `GET /api/v1/tickets/{ticketId}`
- `POST /api/v1/agent-runs/{runId}/cancel`

`GET /health` is public. The other endpoints return `403` without the token
configured by `INTERNAL_API_TOKEN`.

The Java SSE endpoint is:

```text
GET /api/ticket-agent/runs/{runId}/events?lastEventId={eventId}
```

It replays persisted `sp_support_agent_run_event` rows after the cursor and
emits each event with its database ID as the SSE `id`.

## RabbitMQ worker

When the Java platform uses `agent.ticket.dispatch-mode=rabbit`, run the worker:

```powershell
$env:RABBITMQ_URL="amqp://change-me:change-me@localhost:5672/%2F"
$env:ECOMMERCE_API_URL="http://127.0.0.1:8081"
$env:INTERNAL_API_TOKEN="change-me"
.\.venv\Scripts\python.exe -m customer_ticket_copilot.worker
```

The worker consumes `support.agent.run.high`, `support.agent.run.normal`, and
`support.agent.run.low`, runs the analysis workflow, and posts the result back
to the Java internal callback API.

`DRY_RUN=true` returns a deterministic safe fallback without calling an LLM.
Set `DRY_RUN=false` and configure `OPENAI_API_KEY` only after the CrewAI workflow
has been verified against the Java-provided read-only business context.
