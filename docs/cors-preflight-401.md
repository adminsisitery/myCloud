# CORS 预检被网关 401 拦截

> 2026-11-08 · 影响：pan-gateway + 所有带 `Authorization` 头的前端请求

## 现象

前端（`:5173`）调网关（`:8090`），控制台报：

```
blocked by CORS policy: Response to preflight request doesn't pass access control check
```

Network 里只有一条 `OPTIONS /api/files` 返回 **401**，真正的 `GET` 根本没发出去。

## 怎么触发的

三个条件同时成立：

1. **跨源** — 前端 `5173`、网关 `8090`，端口不同即跨源（同源要求协议+域名+端口全同）。
2. **被迫预检** — `pan-sa-token` 配置 `token-name: Authorization`、`is-read-cookie: false`，前端必须手塞 `Authorization` 头。它不在 CORS 安全头列表里，所以请求无法被评为"简单请求"，浏览器必先发 `OPTIONS` 探路。
3. **预检没 token，被鉴权挡掉** — CORS 协议规定预检不带凭证。`MyGlobalFilter` 里 `/api/files` 不在白名单，直接 `StpUtil.checkLogin()` → 抛 `NotLoginException` → 401。

预检失败 → 浏览器判定服务器不允许跨源 → 真请求不发出。

> 补充：`Content-Type: application/json` 也会触发预检，所以 `POST /api/user/login` 同样先发 OPTIONS，但它在白名单里，能过。

**根因**：预检请求在到达业务逻辑前就被鉴权拦了，而预检本身不可能携带凭证。

## 怎么解决的

Nacos `pan-gateways` 加 `globalcors`：

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true
        cors-configurations:
          '[/**]':
            allowed-origin-patterns:
              - "http://localhost:5173"
              - "https://你的生产域名"
            allowed-methods: "*"
            allowed-headers: "*"
            exposed-headers:
              - Content-Disposition
              - Content-Length
            max-age: 3600
```

**为什么能生效**：`globalcors` 挂在 `RoutePredicateHandlerMapping` 上，在路由匹配阶段就把 `OPTIONS` 处理掉返回 204，不走 GlobalFilter 链。所以预检不需要 token，真请求照常带 token 过鉴权。

三个关键项：

- `add-to-simple-url-handler-mapping: true` — 不加上，命中不到路由的预检（如 `/error`）拿不到 CORS 头
- `exposed-headers: Content-Disposition` — 下载接口靠它传文件名，JS 默认读不到非安全响应头，不 expose 下载下来全叫 `download`
- 不加 `allow-credentials` — token 走 header 不走 Cookie，不存在凭据模式

**别自己写 `CorsWebFilter`**：`WebFilter` 容易排在鉴权之后或与鉴权冲突，症状是"预检永远 401"，极难排查。用 `globalcors` 是框架保证的顺序。

## 验证

```bash
# 预检应 204 且带 CORS 头
curl -i -X OPTIONS http://localhost:8090/api/files \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"

# 真请求仍要求 token（401 是对的，证明鉴权没被绕过）
curl -i http://localhost:8090/api/files -H "Origin: http://localhost:5173"
```

## 替代方案

生产环境用 nginx 同域反代，**根本不需要 CORS**：

```nginx
location / { try_files $uri /index.html; }
location /api/ { proxy_pass http://127.0.0.1:8090; }
```

同源了就没事，还能隐藏网关端口。本地开发用 Vite `server.proxy` 代理到 8090，同样不需要。

## 顺带修的另一个问题

`deploy/.env` 里 `OAUTH_GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/github/callback`，**8080 是错的**，网关是 8090，GitHub OAuth 回调会失败。改成 `http://localhost:8090/api/auth/github/callback`（生产换实际域名）。
