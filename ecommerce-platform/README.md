# E-Commerce Platform Backend

Java 17 + Spring Boot 3.3 多模块后端。当前包含商城、订单、售后、秒杀、智能体、工单客服、Gateway、消息分发和监控能力。

完整项目说明、前端与 Python Agent 启动方式见
[根仓库 README](https://github.com/zhouchao078-hue/ecommerce-platform-showcase/blob/main/README.md)。

## 运行服务

| 服务 | 启动类/模块 | 端口 |
| --- | --- | --- |
| Gateway | `gateway` | 8080 |
| commerce-service | `platform-app` | 8081 |
| agent-service | `platform-services/platform-agent-service` | 8082 |

Gateway 路由：

- `/api/chat/**`、`/api/ticket-agent/**`、`/api/support/**`、`/api/internal/ticket-agent/**`、`/api/admin/agent/**` → `agent-service`
- 其他 `/api/**` → `commerce-service`

## 模块

```text
platform-config/                  公共服务配置
platform-common/                  通用结果、异常、分页
platform-framework/               Security、JWT、MyBatis、Redis 配置
platform-api/                     跨模块 DTO、VO 和契约类型
platform-modules/
├── platform-auth/                注册、登录、令牌
├── platform-user/                用户资料、地址、通知
├── platform-merchant/            商家、关注
├── platform-product/            商品、SKU、分类、秒杀、文件
├── platform-cart/                购物车
├── platform-order/               订单、支付、退款、售后
├── platform-agent/               Agent、工具调用、SSE、工单 Run
├── platform-support/             工单事实、审计、人工接管
├── platform-feed/                社区
└── platform-review/              商品评价
platform-app/                     commerce-service 启动模块
platform-services/
└── platform-agent-service/       agent-service 启动模块
gateway/                          Spring Cloud Gateway
deploy/                           Compose、SQL、监控、压测脚本
```

## 构建

要求：

- JDK 17
- Maven 3.9+
- MySQL 8+

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd path\to\ecommerce-project\ecommerce-platform
mvn -B -DskipTests clean package
```

## 本机启动

项目根目录 `.env` 会被服务自动读取：

```powershell
cd path\to\ecommerce-project\ecommerce-platform
.\deploy\start-local-services.ps1 start
.\deploy\start-local-services.ps1 status
```

停止：

```powershell
.\deploy\start-local-services.ps1 stop
```

## Docker 启动

先构建 jar，再构建运行镜像：

```powershell
mvn -B -DskipTests clean package
docker compose -f deploy/docker-compose.services.yml build
docker compose -f deploy/docker-compose.services.yml up -d
```

`deploy/docker-compose.services.yml` 只启动应用服务。MySQL、Redis、Kafka、RabbitMQ、MinIO 等由宿主机或另一个 Compose 文件提供。

## 测试

```powershell
mvn -B test
```

指定模块：

```powershell
mvn -B -pl platform-modules/platform-support -am test
mvn -B -pl platform-services/platform-agent-service -am test
```

## 配置

公共配置位于：

```text
platform-config/src/main/resources/application-common.yml
```

服务配置：

```text
platform-app/src/main/resources/application.yml
platform-services/platform-agent-service/src/main/resources/application.yml
gateway/src/main/resources/application.yml
```

本地真实密钥只放在项目根目录 `.env`。模板见根仓库
[.env.example](https://github.com/zhouchao078-hue/ecommerce-platform-showcase/blob/main/.env.example)。

## 接口响应

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-10-12 12:00:00"
}
```

除登录、注册、刷新令牌和公开浏览接口外，请求需要携带：

```http
Authorization: Bearer <accessToken>
```
