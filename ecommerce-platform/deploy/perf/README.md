# Agent load smoke test

The script uses only the Python standard library and exercises the authenticated
`POST /api/chat` endpoint.

```bash
python deploy/perf/agent-chat-loadtest.py \
  --users 10 \
  --requests-per-user 2 \
  --concurrency 10
```

HTTP `200` responses count as completed Agent requests. HTTP `429` responses
count as controlled backpressure and are expected when per-user or global limits
are reached. Any transport failure or unexpected HTTP status fails the run.

Recommended next-stage tools:

- k6 or JMeter for sustained mixed workloads.
- A separate scenario for open SSE connections.
- Fault injection for Redis, MySQL, Kafka, RabbitMQ, and external LLM timeouts.
- Multi-instance runs to verify that Redis-backed limits are shared.

## Verified run: 2026-10-11

Environment:

- `AGENT_ENV=dev`
- `AGENT_PER_USER_CONCURRENCY=1`
- `AGENT_GLOBAL_CONCURRENCY=4`
- Java 17.0.12, Spring Boot 3.3.0
- Redis-backed session and admission control enabled

Results:

- Agent HTTP regression: 14/14 passed.
- Concurrent smoke test: 20 requests with concurrency 10.
- HTTP statuses: 20 x 200.
- Throughput in dev mode: 229.89 requests/second.
- Latency: average 20.65 ms, p50 18 ms, p95 22 ms, p99 85 ms.
- Forced per-user saturation: HTTP/business code 429 as expected.
- Redis counters returned to 0 after requests completed.
- Redis session history retained the configured 20 entries with TTL.
- Full backend regression: `mvn -B -pl platform-app -am test` passed.

These numbers prove the execution boundary works in dev mode. They are not a
production capacity claim because the deterministic fallback path does not call
an external LLM.

## Reliability additions after the smoke run

- Resilience4j circuit breakers are enabled for supervisor and Ollama calls.
- Micrometer exposes admission, execution duration, LLM call, and LLM duration
  metrics under `agent.*`.
- `/actuator/health` is public; `/actuator/metrics/**` requires `ROLE_ADMIN`.
- Tomcat, HikariCP, and Redis Lettuce limits are configurable through the
  environment variables documented in `.env.example`.

## Distributed AgentRun quota test

```bash
python deploy/perf/ticket-run-quota-test.py --burst 2
```

Run the application with:

```bash
AGENT_TICKET_DISPATCH_MODE=rabbit
AGENT_TICKET_MAX_ACTIVE_RUNS_PER_USER=1
```

The test submits two runs concurrently. Expected result:

- Exactly one PENDING run is created.
- The other request is rejected with business code 429.
- Cancelling the successful run releases the Redis quota.
- A follow-up submission succeeds after cancellation.

## Multi-instance quota and idempotent callback test

Start two application instances with `SERVER_PORT=8081` and
`SERVER_PORT=8082`, using:

```bash
AGENT_TICKET_DISPATCH_MODE=rabbit
AGENT_TICKET_MAX_ACTIVE_RUNS_PER_USER=1
AGENT_TICKET_ACTIVE_RUN_TTL_SECONDS=3600
INTERNAL_API_TOKEN=change-me
```

Run:

```bash
python deploy/perf/ticket-run-multi-instance-test.py \
  --base-urls http://127.0.0.1:8081,http://127.0.0.1:8082
```

Verified on 2026-10-11:

- Cross-instance concurrent submit: exactly one PENDING run and one 429.
- Cross-instance cancel: terminal status `CANCELLED`.
- Quota recovered after cancellation: the next pair again produced one 200
  and one 429.
- Two instances submitted the same result callback concurrently: both returned
  HTTP 200, but only one `AGENT` message was persisted.
- Final run status: `SUCCEEDED`.
- Test report: `passed: true`.

This verifies the Redis-backed quota and database result claim across two
application instances. It does not yet cover outbox duplicate publishing,
simultaneous lease-recovery jobs, or Redis/MySQL/RabbitMQ fault injection.

## Outbox publisher lease recovery test

Apply the schema migration first:

```bash
mysql -uroot -p ecommerce < deploy/sql/support_outbox_lease.sql
```

Start two application instances with:

```bash
AGENT_TICKET_DISPATCH_MODE=rabbit
AGENT_TICKET_OUTBOX_LEASE_SECONDS=3
```

Run:

```bash
python deploy/perf/ticket-outbox-lease-test.py
```

Verified on 2026-10-11:

- A stale `SENDING` row with an expired lease was reclaimed and marked `SENT`.
- A `SENDING` row with a future lease remained owned by the original token.
- After forcing that lease to expire, the row was reclaimed and marked `SENT`.
- A normal `PENDING` row was published and marked `SENT`.
- The script result was `passed: true`.

The publisher now uses `claim_token` plus `lease_expires_at` fencing. A result
can only be finalized by the worker instance that currently owns the claim.

## Isolated dependency fault injection

Start three isolated application instances:

- `8083`: Redis points to an unused port; ticket dispatch uses `local`.
- `8084`: MySQL points to an unused port; ticket dispatch uses `local`.
- `8085`: RabbitMQ points to an unused port; ticket dispatch uses `rabbit`.

Run:

```bash
python deploy/perf/agent-fault-injection-test.py
```

Verified on 2026-10-11:

- Redis unavailable: login remained available; `/api/chat` failed closed with
  business code `429` and `Agent capacity guard unavailable`.
- MySQL unavailable: `/actuator/health` returned `503 DOWN`; login returned a
  controlled `500`; the application kept serving health requests.
- RabbitMQ unavailable: the outbox row was returned to `PENDING` with
  `retry_count >= 1` and a connection-refused error, ready for retry.
- Final script result: `passed: true`.

The test uses isolated ports and does not stop the user's running MySQL or
RabbitMQ services.

## Concurrent AgentRun lease recovery test

Start two instances with:

```bash
AGENT_TICKET_DISPATCH_MODE=rabbit
AGENT_TICKET_LEASE_RECOVERY_INTERVAL_MS=500
AGENT_TICKET_LEASE_RECOVERY_BATCH_SIZE=50
```

Run:

```bash
python deploy/perf/ticket-run-lease-recovery-race-test.py --runs 40
```

The script inserts 40 expired `RUNNING` runs, then verifies:

- Every run returns to `PENDING`.
- Every run has exactly one `LEASE_RECOVERED` step.
- Every run has exactly one recovered Outbox event.
- No recovery is duplicated or missing.

Verified on 2026-10-11 with `passed: true`.

## Ticket-agent SSE long-connection test

Start the application with:

```bash
AGENT_SSE_SCHEDULER_POOL_SIZE=4
AGENT_SSE_HEARTBEAT_MILLIS=500
AGENT_SSE_TIMEOUT_SECONDS=30
```

Run with one or more instances:

```bash
python deploy/perf/ticket-sse-loadtest.py \
  --base-urls http://127.0.0.1:8081,http://127.0.0.1:8082 \
  --connections 40
```

The script creates a long-running RUNNING run, opens concurrent SSE streams,
and verifies every connection receives:

- The initial `run.status` snapshot.
- A subsequent `ping` heartbeat.

Verified on 2026-10-12:

- 40 concurrent SSE connections across two instances.
- 40/40 connections received both snapshot and heartbeat.
- 0 failures; maximum elapsed time was 1656 ms.

The controller also cancels its scheduled poller when the client disconnects.

## Real-model concurrency test

Start the application with a real provider key and explicit limits:

```bash
AGENT_ENV=prod
AGENT_GLOBAL_CONCURRENCY=16
AGENT_PER_USER_CONCURRENCY=2
AGENT_LLM_CONCURRENCY=4
AGENT_LLM_ACQUIRE_TIMEOUT_MILLIS=500
AGENT_LLM_TIMEOUT_SECONDS=60
```

Run a small, bounded test:

```bash
python deploy/perf/agent-real-model-concurrency-test.py \
  --safe-users 2 \
  --stress-users 4
```

The script creates temporary users, runs real `/api/chat` product requests,
checks for controlled backpressure or fallback, removes the users, and reports
latency. Verified on 2026-10-12:

- Safe phase: 2/2 successful real-model responses.
- Stress phase: 4/4 successful responses.
- Server errors: 0.
- Safe-phase latency: 2795 ms to 2952 ms.
- Stress-phase latency: 2031 ms to 3233 ms.

## Production monitoring stack

From `ecommerce-platform/deploy`:

```bash
docker compose up -d prometheus alertmanager grafana
```

See `deploy/monitoring/README.md` for URLs and alert details.

Verified on 2026-10-12:

- Prometheus target on `8081` was `UP`.
- Prometheus loaded all 9 alert rules.
- `agent_execution_admission_total` and `agent_execution_queue_size` were
  scraped successfully.
- Grafana loaded the provisioned `Ecommerce Agent Overview` dashboard.
- Alertmanager was ready.

External alert delivery is configured in the project `.env`:

```properties
FEISHU_BOT_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/your-token
FEISHU_BOT_SECRET=
FEISHU_BOT_MENTION_USER_IDS=
FEISHU_BOT_MENTION_ALL=false
ALERT_WEBHOOK_SEND_RESOLVED=true
```

Alertmanager routes matching alerts through the internal `alert-relay`, which
formats them for the Feishu bot. If no Feishu URL or generic webhook is
configured, alerts remain available in the Alertmanager UI but are not sent
externally.

## Seckill Concurrency Test

The seckill test creates an activity through the merchant account, registers
concurrent buyers, and verifies that success count and remaining stock match
the configured stock. It also retries a successful purchase and requires the
same order number to be returned.

```powershell
python deploy/perf/seckill-concurrency-test.py `
  --base-url http://127.0.0.1:8080/api `
  --merchant-username testuser `
  --merchant-password change-me `
  --product-id 3 `
  --users 25 `
  --stock 10 `
  --workers 25
```

Each product can have only one seckill activity, so use an unused product ID
or reset the test activity before running the script again.
