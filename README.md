# E-Commerce Platform

[![CI/CD Pipeline](https://github.com/your-account/ecommerce-platform/actions/workflows/pipeline.yml/badge.svg)](https://github.com/your-account/ecommerce-platform/actions/workflows/pipeline.yml)

一个面向学习、演示和二次开发的电商平台项目，覆盖用户商城、商家经营、订单交易、售后、秒杀、智能客服、多智能体协作、可观测性和渐进式微服务部署。

项目重点不只是完成传统电商 CRUD，而是把以下能力放在同一条工程链路中验证：

- 用户、商家和平台管理三个业务视角。
- 商品、SKU、购物车、跨店拆单、支付、售后和通知。
- Java 负责交易事实，Python CrewAI 负责智能体分析。
- Redis、Kafka、RabbitMQ、MinIO 等基础设施的实际接入。
- 多并发、限流、熔断、幂等、Outbox、SSE 和故障恢复。
- Spring Cloud Gateway 路由和独立服务部署。
- Prometheus、Grafana、Alertmanager 和飞书机器人告警。

> 当前定位是功能完整、适合学习和作品集展示的项目。支付使用模拟渠道，商城服务仍处于渐进式微服务拆分阶段，不应直接作为未经加固的生产资金系统使用。

> 本目录是脱敏后的展示包。真实 `.env`、密钥、Git 历史、IDE 配置、构建产物和测试日志均已移除。第一次启动请看 [QUICKSTART.md](QUICKSTART.md)，脱敏说明见 [DEMO-NOTICE.md](DEMO-NOTICE.md)。

## 目录

- [功能概览](#功能概览)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [环境要求](#环境要求)
- [环境配置](#环境配置)
- [启动 MySQL](#启动-mysql)
- [启动基础中间件](#启动基础中间件)
- [启动后端](#启动后端)
- [启动 Python 工单 Agent](#启动-python-工单-agent)
- [启动前端](#启动前端)
- [测试](#测试)
- [CI/CD](#cicd)
- [接口与文档](#接口与文档)
- [监控和告警](#监控和告警)
- [常见问题](#常见问题)
- [开发约定](#开发约定)
- [当前限制](#当前限制)

## 功能概览

### 用户端

- 注册、登录、JWT 鉴权、刷新令牌。
- 商品浏览、搜索、分类、商品详情、店铺主页。
- 商品 SKU、购物车、下单、模拟支付、订单查询。
- 收货地址管理和默认地址。
- 关注商品、关注商家、站内通知。
- 售后退款、退货退款、填写退货物流。
- 社区广场（论坛）、商品评价和秒杀页面。
- 智能聊天助手、商品推荐卡片和工单客服。

### 商家端

- 商家入驻和审核状态。
- 商品、SKU、分类、图片和库存管理。
- 商家订单查看、发货。
- 售后审核、退款和退货收货确认。
- 销售、订单、商品、关注和售后数据看板。
- 创建秒杀活动。

### 管理端

- 商家审核。
- 智能体运行审计、步骤追踪和质量统计。
- 人工接管、人工回复、释放和关闭工单。
- 智能体结果评分与提示词版本追踪。

### 智能体

- Supervisor、Commerce、Pet、Support 等 Agent 角色。
- 工具调用，包括商品检索、商品推荐、购物车和订单查询。
- SSE 流式输出、心跳、超时、取消和断点恢复。
- LLM 调用并发限制、熔断、队列和退避。
- Java 与 Python CrewAI 之间的契约和内部 Token 校验。
- Outbox、RabbitMQ/Kafka 分发、Run 租约和崩溃恢复。

## 系统架构

```text
┌──────────────────────┐
│ Vue 3 Frontend :5173 │
└──────────┬───────────┘
           │ /api
           ▼
┌──────────────────────┐
│ Spring Cloud Gateway │ :8080
└───────┬──────────────┘
        │
        ├──────────────────────────────┐
        │                              │
        ▼                              ▼
┌───────────────────┐         ┌───────────────────┐
│ commerce-service  │ :8081   │ agent-service     │ :8082
│ 商城、订单、售后   │         │ 聊天、工单、Agent │
└─────────┬─────────┘         └─────────┬─────────┘
          │                              │
          │                              ▼
          │                    ┌──────────────────────┐
          │                    │ Python CrewAI :8000  │
          │                    │ 分析、RAG、回复草稿  │
          │                    └──────────────────────┘
          │
          ▼
┌────────────────────────────────────────────────────┐
│ MySQL / Redis / RabbitMQ / Kafka / MinIO / Ollama │
└────────────────────────────────────────────────────┘
```

Gateway 路由规则：

| 路径 | 目标服务 |
| --- | --- |
| `/api/chat/**` | `agent-service` |
| `/api/ticket-agent/**` | `agent-service` |
| `/api/support/**` | `agent-service` |
| `/api/internal/ticket-agent/**` | `agent-service` |
| `/api/admin/agent/**` | `agent-service` |
| 其他 `/api/**` | `commerce-service` |

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3、Spring Security、JWT |
| 数据访问 | MyBatis-Plus、MySQL 8 |
| 中间件 | Redis 7、RabbitMQ、Kafka、ZooKeeper |
| 对象存储 | MinIO |
| 微服务 | Spring Cloud Gateway、独立 Spring Boot 服务 |
| 容错 | Resilience4j、Redis 配额、Outbox、租约恢复 |
| 智能体 | Java 编排、Python CrewAI、FastAPI、SSE |
| 前端 | Vue 3、Vite、TypeScript、Pinia、Vue Router、Element Plus |
| 可观测性 | Prometheus、Grafana、Alertmanager、MeterRegistry |
| 构建 | Maven 多模块、npm、Docker Compose |

## 仓库结构

```text
ecommerce-project/
├── README.md
├── .env.example
├── contracts/
│   └── ticket-agent.openapi.yaml
├── customer-ticket-copilot/       # Python CrewAI 工单 Agent
├── ecommerce-frontend/            # Vue 3 前端
└── ecommerce-platform/            # Java 后端 Git submodule
    ├── gateway/                   # Gateway :8080
    ├── platform-app/              # commerce-service :8081
    ├── platform-config/           # 公共服务配置
    ├── platform-common/
    ├── platform-framework/
    ├── platform-api/
    ├── platform-modules/
    ├── platform-services/
    │   └── platform-agent-service/ # agent-service :8082
    └── deploy/
        ├── docker-compose.yml
        ├── docker-compose.services.yml
        ├── Dockerfile.service
        ├── monitoring/
        ├── perf/
        ├── sql/
        └── start-local-services.ps1
```

后端位于 Git submodule 中。克隆项目时需要初始化 submodule：

```powershell
git clone --recurse-submodules https://github.com/your-account/ecommerce-platform.git
cd ecommerce-project
```

已经克隆但目录为空时：

```powershell
git submodule update --init --recursive
```

## 环境要求

推荐环境：

| 依赖 | 版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9+ |
| Node.js | 20+ |
| npm | 10+ |
| Python | 3.11+ |
| MySQL | 8.0+ |
| Docker Desktop | 启用 Compose v2 |

验证：

```powershell
java -version
mvn -version
node -v
npm -v
python --version
docker compose version
```

## 环境配置

所有本地密钥和可变配置统一放在项目根目录 `.env`，该文件已被 Git 忽略。首次启动前：

```powershell
Copy-Item .env.example .env
```

至少需要检查以下配置。

### 服务端口

```properties
GATEWAY_PORT=8080
COMMERCE_SERVICE_PORT=8081
AGENT_SERVICE_PORT=8082
COMMERCE_SERVICE_URI=http://127.0.0.1:8081
AGENT_SERVICE_URI=http://127.0.0.1:8082
VITE_API_PROXY_TARGET=http://localhost:8080
```

### MySQL

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ecommerce?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=change-me
```

### Redis、Kafka、RabbitMQ、MinIO

```properties
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=change-me

SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=change-me
RABBITMQ_PASSWORD=change-me

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=change-me
MINIO_SECRET_KEY=change-me
MINIO_BUCKET=ecommerce
```

### 模型与 Agent

```properties
agent.supervisor.model=deepseek-chat
agent.supervisor.api-key=your-deepseek-api-key
agent.supervisor.endpoint=https://api.deepseek.com/v1/chat/completions

agent.commerce.model=gpt-4o
agent.commerce.api-key=your-openai-api-key
agent.commerce.endpoint=https://api.openai.com/v1/chat/completions

agent.local.model=llama3.2
AGENT_LOCAL_ENDPOINT=http://localhost:11434/api/chat

AGENT_TICKET_BASE_URL=http://127.0.0.1:8000
AGENT_TICKET_MODEL=deepseek-chat
AGENT_TICKET_PROMPT_VERSION=support-agent-v1
INTERNAL_API_TOKEN=change-me
```

### 飞书告警

```properties
FEISHU_BOT_WEBHOOK_URL=
FEISHU_BOT_SECRET=
FEISHU_BOT_MENTION_USER_IDS=
FEISHU_BOT_MENTION_ALL=false
ALERT_WEBHOOK_SEND_RESOLVED=true
```

`FEISHU_BOT_WEBHOOK_URL` 为空时，告警只保留在 Alertmanager UI，不向外部发送。

完整变量见 [.env.example](.env.example)。不要提交真实 API Key、数据库密码或 Webhook 地址。

## 启动 MySQL

`deploy/docker-compose.yml` 当前不包含 MySQL 服务。请使用本机 MySQL 或自行增加 MySQL 容器。

创建数据库：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS ecommerce DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

导入初始化脚本：

```powershell
Get-Content .\ecommerce-platform\deploy\sql\init.sql -Raw |
  mysql -uroot -p ecommerce
```

如果已经执行过旧版脚本，请在升级前备份数据库，并按变更内容补跑 `deploy/sql/` 下的增量 SQL。

## 启动基础中间件

基础组件：

```powershell
cd ecommerce-platform
docker compose -f deploy/docker-compose.yml up -d redis rabbitmq zookeeper kafka minio
```

完整监控栈：

```powershell
docker compose -f deploy/docker-compose.yml up -d prometheus alertmanager alert-relay grafana
```

查看状态：

```powershell
docker compose -f deploy/docker-compose.yml ps
```

默认端口：

| 服务 | 地址 |
| --- | --- |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672` |
| RabbitMQ Console | `http://localhost:15672` |
| Kafka | `localhost:9092` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| Prometheus | `http://localhost:9090` |
| Alertmanager | `http://localhost:9093` |
| Grafana | `http://localhost:3000` |

开发环境默认账号只用于本地测试，公开部署前必须替换。

## 启动后端

### 方式一：本机多进程

先设置 `JAVA_HOME`。下面的 Maven 路径按实际安装位置替换：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd path\to\ecommerce-project\ecommerce-platform
mvn -B -DskipTests clean package
```

启动三个服务：

```powershell
.\deploy\start-local-services.ps1 start
.\deploy\start-local-services.ps1 status
```

停止：

```powershell
.\deploy\start-local-services.ps1 stop
```

服务地址：

| 服务 | 地址 |
| --- | --- |
| Gateway | `http://localhost:8080` |
| commerce-service | `http://localhost:8081` |
| agent-service | `http://localhost:8082` |

只启动旧版商城单体也可以：

```powershell
cd platform-app
mvn spring-boot:run
```

这种方式不会启动 Gateway 和独立 `agent-service`。接入完整智能体链路时请使用脚本启动三个服务。

### 方式二：Docker 服务镜像

项目当前采用“本机构建 jar，容器只负责运行”的发布方式：

```powershell
cd path\to\ecommerce-project\ecommerce-platform
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'

mvn -B -DskipTests clean package
docker compose -f deploy/docker-compose.services.yml build
docker compose -f deploy/docker-compose.services.yml up -d
```

停止：

```powershell
docker compose -f deploy/docker-compose.services.yml down
```

容器通过 `host.docker.internal` 访问宿主机上的 MySQL、Redis、Kafka、RabbitMQ、MinIO、Ollama 和 Python Agent。

## 启动 Python 工单 Agent

```powershell
cd path\to\ecommerce-project\customer-ticket-copilot
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
Copy-Item .env.example .env
```

启动 HTTP 服务：

```powershell
.\.venv\Scripts\python.exe -m uvicorn customer_ticket_copilot.api:app --host 0.0.0.0 --port 8000
```

健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

更多配置见 [customer-ticket-copilot/README.md](customer-ticket-copilot/README.md)。

## 启动前端

根目录 `.env` 中的 `VITE_API_PROXY_TARGET` 应指向 Gateway：

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

安装并启动：

```powershell
cd path\to\ecommerce-project\ecommerce-frontend
npm ci
npm run dev
```

访问：

```text
http://localhost:5173
```

生产构建：

```powershell
npm run build
```

## 测试

### 后端

```powershell
cd path\to\ecommerce-project\ecommerce-platform
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
mvn -B test
```

`platform-app` 通过本地 Maven 仓库读取兄弟模块时，使用：

```powershell
mvn -B -pl platform-app -am test -DskipTests=false
```

### 前端

```powershell
cd path\to\ecommerce-project\ecommerce-frontend
npm run build
```

### Python Agent

```powershell
cd path\to\ecommerce-project\customer-ticket-copilot
.\.venv\Scripts\python.exe -m pytest -q
```

### 秒杀并发

```powershell
cd path\to\ecommerce-project\ecommerce-platform
python deploy\perf\seckill-concurrency-test.py `
  --base-url http://127.0.0.1:8080/api `
  --merchant-username testuser `
  --merchant-password change-me `
  --product-id 3 `
  --users 25 `
  --stock 10 `
  --workers 25
```

脚本会验证：

- 成功订单数等于可售库存。
- 最终剩余库存正确。
- 重复请求返回同一个订单号。

更多压测脚本见 [deploy/perf/README.md](ecommerce-platform/deploy/perf/README.md)。

## CI/CD

仓库包含 GitHub Actions 流水线：

[.github/workflows/pipeline.yml](.github/workflows/pipeline.yml)

流水线分三个阶段：

```text
validate
  -> 后端 Maven 测试
  -> 前端 npm build
  -> Python Agent pytest

package
  -> 构建 Gateway、commerce-service、agent-service
  -> 构建 Docker 镜像
  -> 推送 GitHub Container Registry

deploy
  -> SSH 拉取最新代码
  -> 拉取指定 Commit 的镜像
  -> 更新 production environment
  -> 检查 Gateway 健康状态
```

Pull Request 只执行 `validate`，不会推送镜像或部署。主分支推送会执行 `validate` 和 `package`。

### 启用生产部署

默认不会自动部署。需要先配置 GitHub Actions。

Repository Variable：

```text
DEPLOY_ENABLED=true
```

Production Environment Secrets：

| Secret | 说明 |
| --- | --- |
| `DEPLOY_HOST` | 部署服务器地址 |
| `DEPLOY_PORT` | SSH 端口，通常是 `22` |
| `DEPLOY_USER` | SSH 用户 |
| `DEPLOY_SSH_KEY` | 可登录服务器的私钥 |
| `DEPLOY_PATH` | 服务器上的仓库根目录 |

生产服务器还需要：

1. 已克隆根仓库和 submodule。
2. 已安装 Docker 和 Compose v2。
3. 项目根目录已有正确的 `.env`。
4. 如 GHCR 镜像是私有包，已执行过 `docker login ghcr.io`。
5. 当前用户可以在 `DEPLOY_PATH` 执行 Docker Compose。

GitHub Environment `production` 可以配置 Required Reviewers，使部署必须人工批准。

每次发布的镜像标签使用前 12 位 Commit SHA：

```text
ghcr.io/<owner>/<repo>/commerce-service:<sha>
ghcr.io/<owner>/<repo>/agent-service:<sha>
ghcr.io/<owner>/<repo>/gateway:<sha>
```

同时会更新 `latest` 标签，但生产部署固定使用 SHA，避免 `latest` 漂移。

## 接口与文档

- Gateway 入口：`http://localhost:8080/api`
- Actuator 健康检查：`/actuator/health`
- Prometheus 指标：`/actuator/prometheus`
- 工单 Agent OpenAPI 契约：[contracts/ticket-agent.openapi.yaml](contracts/ticket-agent.openapi.yaml)

项目当前没有在主服务中启用 Swagger UI 或 Knife4j 依赖，接口契约以 Controller、OpenAPI 文件和测试为准。

请求鉴权格式：

```http
Authorization: Bearer <accessToken>
```

统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-10-12 12:00:00"
}
```

## 监控和告警

- Prometheus 抓取 Gateway、commerce-service 和 agent-service 的 Actuator 指标。
- Grafana 预置 `Ecommerce Agent Overview` 看板。
- Alertmanager 处理分组、抑制、恢复通知和重复提醒。
- 飞书通知通过内部 `alert-relay` 转换 Alertmanager webhook。

监控说明见 [deploy/monitoring/README.md](ecommerce-platform/deploy/monitoring/README.md)。

## 常见问题

### `docker compose up` 找不到文件

Compose 文件位于 `ecommerce-platform/deploy/`，必须显式指定：

```powershell
docker compose -f deploy/docker-compose.yml up -d
```

### 3306 端口被占用

项目默认连接本机 `127.0.0.1:3306`。先检查：

```powershell
Get-NetTCPConnection -LocalPort 3306
```

如果 MySQL 已经在运行，不需要再启动 MySQL 容器。

### `vite` 不是可执行命令

```powershell
cd ecommerce-frontend
npm ci
npm run dev
```

### 前端请求返回连接失败

确认 Gateway 已启动，并且根目录 `.env` 中：

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

### 智能体接口返回 401

这是未携带 JWT 时的正常行为。先登录并携带 Bearer Token。

### Docker 服务镜像构建提示找不到 jar

先完成后端打包：

```powershell
mvn -B -DskipTests clean package
```

然后执行服务镜像构建。

## 开发约定

- Controller 只处理请求、参数校验和响应。
- 业务逻辑放在 ServiceImpl。
- Controller 不直接使用 `SecurityContextHolder`，通过认证服务获取当前用户。
- 资金、库存、订单状态变更必须考虑幂等、事务和并发。
- 提交前至少运行后端测试和前端构建。
- 不提交 `.env`、真实密钥、构建产物和本地测试日志。

## 当前限制

- 支付渠道是模拟实现，尚未接入支付宝或微信支付。
- `commerce-service` 仍承担多个商城领域，后续可继续拆分身份、交易、商品和社区服务。
- 服务地址目前以环境变量和 Compose DNS 配置为主，生产环境可接入 Kubernetes DNS 或注册中心。
- 公开部署前需要补充生产密钥管理、数据库迁移工具、CI/CD、备份恢复和更完整的风控。

## 参与贡献

1. 从最新主分支创建功能分支。
2. 保持修改聚焦，不要混合无关重构。
3. 补充或更新测试。
4. 提交前运行对应模块的测试和构建。
5. 提交 Pull Request，并在描述中说明行为变化、测试结果和风险。

## License

当前仓库尚未声明开源许可证。如计划公开分发或允许商业使用，请先补充明确的 `LICENSE` 文件。
