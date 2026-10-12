# Agent Monitoring Stack

The monitoring stack adds Prometheus, Alertmanager, and Grafana to
`deploy/docker-compose.yml`.

## Start

From `ecommerce-platform/deploy`:

```bash
docker compose up -d prometheus alertmanager grafana
```

Start one or two application instances on `8081` and/or `8082`.

## URLs

- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3000`
- Grafana login: `admin` / `admin`

The provisioned dashboard is `Ecommerce Agent Overview`.

## Scraped Metrics

Prometheus scrapes:

```text
http://host.docker.internal:8081/actuator/prometheus
http://host.docker.internal:8082/actuator/prometheus
```

The application exposes Prometheus metrics without authentication so the
scraper can reach them. In production this endpoint should be restricted to the
monitoring network or protected by infrastructure-level authentication.

## Built-In Alerts

- LLM failure rate above 20%.
- LLM p95 latency above 8 seconds.
- Agent admission rejections.
- Agent execution timeouts.
- Agent queue backlog.
- Ticket-agent SSE connections above 100.
- Ticket Outbox publish or claim failures.
- Ticket Run quota rejections.
- HikariCP pending connection pressure.

### Feishu Bot

Create a custom bot in the target Feishu group, then configure the project
`.env`:

```properties
FEISHU_BOT_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/your-token
FEISHU_BOT_SECRET=
FEISHU_BOT_MENTION_USER_IDS=
FEISHU_BOT_MENTION_ALL=false
ALERT_WEBHOOK_SEND_RESOLVED=true
```

If the bot has signature verification enabled, put the signing secret in
`FEISHU_BOT_SECRET`. `FEISHU_BOT_MENTION_USER_IDS` accepts a comma-separated
list of Feishu user IDs. Set `FEISHU_BOT_MENTION_ALL=true` to mention everyone.

When `FEISHU_BOT_WEBHOOK_URL` is set, `alertmanager.sh` automatically routes
alerts to the internal `alert-relay`, which converts the Alertmanager payload
into a Feishu message. `ALERT_WEBHOOK_URL` can still override that destination
for a different webhook implementation.

Restart the relay and Alertmanager after changing `.env`:

```bash
docker compose up -d --force-recreate alert-relay alertmanager
```

If no Feishu URL or generic webhook is configured, alerts remain visible in
the Alertmanager UI but are not sent externally.
