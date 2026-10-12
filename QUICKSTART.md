# Quick Start

本文档面向第一次拿到展示包的人。推荐使用 Windows 10/11、PowerShell 和 Docker Desktop。

## 1. 环境要求

| 软件 | 建议版本 |
| --- | --- |
| JDK | 17 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Python | 3.11 或 3.12 |
| MySQL | 8.0+ |
| Docker Desktop | Compose v2 |

检查环境：

```powershell
java -version
mvn -version
node -v
npm -v
python --version
docker compose version
```

## 2. 解压并配置环境变量

在展示包根目录执行：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少修改：

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/ecommerce?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=你的MySQL密码

SPRING_DATA_REDIS_PASSWORD=你的Redis密码
REDIS_PASSWORD=你的Redis密码
```

如果只演示普通商城，暂时可以不配置模型 API Key。

如果要测试真实大模型聊天，再配置：

```properties
agent.supervisor.api-key=你的DeepSeekKey
agent.commerce.api-key=你的OpenAIKey
INTERNAL_API_TOKEN=自己设置一个内部Token
```

## 3. 初始化 MySQL

确认 MySQL 已在本机 `3306` 启动：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS ecommerce DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

导入表结构：

```powershell
Get-Content .\ecommerce-platform\deploy\sql\init.sql -Raw |
  mysql -uroot -p ecommerce
```

## 4. 启动中间件

最小商城演示只需要 Redis。需要商品图片时同时启动 MinIO：

```powershell
cd ecommerce-platform
docker compose -f deploy/docker-compose.yml up -d redis minio
```

完整演示包括 RabbitMQ、Kafka 和监控：

```powershell
docker compose -f deploy/docker-compose.yml up -d redis rabbitmq zookeeper kafka minio
docker compose -f deploy/docker-compose.yml up -d prometheus alertmanager alert-relay grafana
```

## 5. 启动 Java 后端

```powershell
cd ecommerce-platform
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B -DskipTests clean package
.\deploy\start-local-services.ps1 start
.\deploy\start-local-services.ps1 status
```

也可以使用 Docker：

```powershell
docker compose -f deploy/docker-compose.services.yml build
docker compose -f deploy/docker-compose.services.yml up -d
```

服务地址：

| 服务 | 地址 |
| --- | --- |
| Gateway | `http://localhost:8080` |
| commerce-service | `http://localhost:8081` |
| agent-service | `http://localhost:8082` |

## 6. 启动 Python 工单 Agent

只演示商城时可以跳过这一步。需要智能工单时执行：

```powershell
cd customer-ticket-copilot
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
Copy-Item .env.example .env
```

编辑 `customer-ticket-copilot/.env`：

```properties
OPENAI_API_KEY=你的模型Key
DRY_RUN=false
INTERNAL_API_TOKEN=与根目录.env保持一致
```

只想验证接口、不调用真实模型时：

```properties
OPENAI_API_KEY=
DRY_RUN=true
INTERNAL_API_TOKEN=change-me
```

启动：

```powershell
.\.venv\Scripts\python.exe -m uvicorn customer_ticket_copilot.api:app --host 0.0.0.0 --port 8000
```

## 7. 启动前端

```powershell
cd ecommerce-frontend
npm ci
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

前端通过根目录 `.env` 中的以下配置访问 Gateway：

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

## 8. 创建演示账号

初始化脚本只创建角色，不创建真实用户。先在注册页面注册普通账号。

### 普通买家

注册后即可演示商品、购物车、订单、地址等流程。

### 管理员

先注册一个账号，然后在 MySQL 中授予管理员角色：

```sql
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.code = 'ROLE_ADMIN'
WHERE u.username = '你的用户名';
```

重新登录后即可看到管理员入口。

### 商家

1. 注册商家账号。
2. 登录后在用户中心提交商家入驻。
3. 使用管理员账号审核通过。
4. 重新登录后进入商家工作台。

## 9. 停止服务

本机 Java 服务：

```powershell
cd ecommerce-platform
.\deploy\start-local-services.ps1 stop
```

Docker 应用服务：

```powershell
docker compose -f ecommerce-platform/deploy/docker-compose.services.yml down
```

中间件：

```powershell
docker compose -f ecommerce-platform/deploy/docker-compose.yml down
```

## 10. 常见问题

### 前端请求连接失败

确认 Gateway `8080` 已启动，并且 `VITE_API_PROXY_TARGET=http://localhost:8080`。

### 智能体接口返回 401

先登录并在请求中携带 Bearer Token。

### 智能工单调用失败

确认 Python Agent 已监听 `8000`，并检查根目录 `.env` 和
`customer-ticket-copilot/.env` 中的 `INTERNAL_API_TOKEN` 是否一致。

### Docker 找不到镜像

先执行 Maven 打包，再运行：

```powershell
docker compose -f deploy/docker-compose.services.yml build
```

### 3306 端口被占用

本机 MySQL 已经运行时，不需要再启动 MySQL 容器。
