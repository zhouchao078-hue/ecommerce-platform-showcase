# Independent Services

The backend now has three independently started Spring Boot processes:

| Service | Port | Responsibilities |
| --- | --- | --- |
| `commerce-service` | 8081 | Auth, user, merchant, product, cart, order, community |
| `agent-service` | 8082 | Chat agents, support tickets, ticket-agent runs |
| `gateway` | 8080 | Single public API entry point and path routing |

## Build

```powershell
cd path\to\ecommerce-project\ecommerce-platform
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& 'mvn' -B -DskipTests clean package
```

## Run

```powershell
cd path\to\ecommerce-project\ecommerce-platform
.\deploy\start-local-services.ps1 start
.\deploy\start-local-services.ps1 status
.\deploy\start-local-services.ps1 stop
```

The frontend should target `http://localhost:8080`. Direct service ports are for
local diagnostics only.

## Gateway Routes

- `/api/chat/**`, `/api/ticket-agent/**`, `/api/support/**`,
  `/api/internal/ticket-agent/**`, `/api/admin/agent/**` -> `agent-service`.
- Every other `/api/**` request -> `commerce-service`.

Service addresses can be overridden with `AGENT_SERVICE_URI` and
`COMMERCE_SERVICE_URI`.

## Docker

Build the executable jars first, then build and start the service images:

```powershell
cd path\to\ecommerce-project\ecommerce-platform
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& 'mvn' -B -DskipTests clean package
docker compose -f deploy/docker-compose.services.yml build
docker compose -f deploy/docker-compose.services.yml up -d
```

The containers use `host.docker.internal` to reach MySQL, Redis, Kafka,
RabbitMQ, MinIO, Ollama, and the Python ticket copilot running on the host.
Stop them with:

```powershell
docker compose -f deploy/docker-compose.services.yml down
```
