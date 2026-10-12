# E-Commerce Frontend

电商平台 Vue 3 前端，包含用户商城、商家工作台、订单售后、通知、秒杀、工单客服和智能体控制台。

完整项目和后端启动说明见 [根仓库 README](../README.md)。

## 技术栈

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus
- Axios
- Microsoft Fetch Event Source，用于 SSE

## 环境要求

- Node.js 20+
- npm 10+

## 安装

```powershell
cd path\to\ecommerce-project\ecommerce-frontend
npm ci
```

## 开发环境

前端通过项目根目录 `.env` 读取 API 代理地址：

```properties
VITE_API_PROXY_TARGET=http://localhost:8080
```

启动：

```powershell
npm run dev
```

访问：

```text
http://localhost:5173
```

所有 `/api` 请求由 Vite 代理到 Gateway。正式运行时不建议让浏览器直接访问内部服务端口。

## 生产构建

```powershell
npm run build
```

本地预览构建结果：

```powershell
npm run preview
```

## 目录

```text
src/
├── api/           Axios 接口和类型
├── components/    通用组件
├── layouts/       页面布局
├── router/        路由和权限守卫
├── stores/        Pinia 状态
├── views/         页面
├── App.vue
├── main.ts
└── style.css
```

## 权限和 Token

- Access Token 和用户信息由 Pinia 持久化管理。
- 路由守卫处理登录、商家和管理员权限。
- Axios 请求自动携带 `Authorization: Bearer <accessToken>`。
- Token 失效时会清理登录态并返回登录页。
