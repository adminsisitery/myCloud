# Nacos 配置：网关 CORS（globalcors）

> 2026-11-08 · 目标配置：`pan-gateways`（group = `pan`）
> 相关背景见 `docs/cors-preflight-401.md`

---

## 一、为什么放在 Nacos 的 `pan-gateways`

网关的 `application.yaml` 只保留启动引导配置，业务配置全部托管 Nacos。CORS 属于网关行为，和路由放在一起最合理：路由和跨域规则同源管理，改一处不用翻两处。

本地 `application.yaml` 已带一份默认值（`CORS_ALLOWED_ORIGINS` 默认 `http://localhost:5173`），**即使 Nacos 没配也能跑起来**。Nacos 里配了就以 Nacos 为准（Spring 的 `spring.config.import` 优先级高于本地）。

---

## 二、要加的内容

### 方式 A：追加到现有 `pan-gateways`（推荐）

在现有的 `spring.cloud.gateway.routes` 同级下追加 `globalcors`：

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ……原有路由保持不变，此处省略……

      # ===== 新增：跨域配置 =====
      globalcors:
        add-to-simple-url-handler-mapping: true
        cors-configurations:
          '[/**]':
            allowed-origin-patterns:
              - "http://localhost:5173"
              - "http://127.0.0.1:5173"
              - "https://你的生产域名"
            allowed-methods: "*"
            allowed-headers: "*"
            exposed-headers:
              - Content-Disposition
              - Content-Length
            max-age: 3600
```

### 方式 B：单独建 `pan-gateway-cors`（便于灰度/回滚）

如果不想动 `pan-gateways`，可以新建一个 dataId：

| 项 | 值 |
|---|---|
| Data ID | `pan-gateway-cors` |
| Group | `pan` |
| 格式 | YAML |

内容是上面 `globalcors` 那段（顶层带 `spring:`）。

然后要在网关的 `application.yaml` 里加一行 import：

```yaml
spring:
  config:
    import:
      # ……原有 5 行保持不变……
      - "optional:nacos:pan-gateway-cors?group=pan&refreshEnabled=true"
```

**代价**：多一个 dataId、多一行代码。除非需要单独回滚，否则用方式 A。

---

## 三、每个配置项为什么这么写

| 配置 | 作用 | 不写会怎样 |
|---|---|---|
| `add-to-simple-url-handler-mapping: true` | 让匹配不到路由的请求也能拿到 CORS 头 | 命中不到路由的预检（如 `/error`）没有 CORS 响应头，前端还是报错 |
| `allowed-origin-patterns` | 允许来源白名单，支持通配符 | 用 `allowed-origins` 也行，但一旦以后开 `allow-credentials` 就不能写 `*`，直接用 patterns 省事 |
| `allowed-methods: "*"` | 允许的方法 | 必填。不写默认只允许 GET/HEAD |
| `allowed-headers: "*"` | 允许的请求头 | **必须写**。`Authorization` 是自定义头，不放开预检直接失败 |
| `exposed-headers` | 允许 JS 读取的响应头 | 下载接口的 `Content-Disposition` 读不到，前端拿不到文件名 |
| `max-age: 3600` | 预检结果缓存 1 小时 | 每个请求都多一次 OPTIONS 往返，性能损耗 |

**不要加 `allow-credentials: true`**：本项目 token 走 `Authorization` 头不走 Cookie（`is-read-cookie: false`），不存在凭据模式。加了反而受限——`allowedOrigins` 就不能用通配符。

---

## 四、验证步骤

改完 Nacos 后，先在 Nacos 控制台点「发布」。`refreshEnabled=true` 会热刷新，但 CORS 配置涉及 handler mapping 初始化，**建议重启网关**确保生效：

```bash
docker restart mycloud-gateway
# 或 systemd 部署
systemctl restart mycloud-gateway
```

然后跑三条 curl：

```bash
# ① 预检：应返回 204，且带 CORS 头
curl -i -X OPTIONS http://localhost:8090/api/files \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"

# 期望：
# HTTP/1.1 204
# Access-Control-Allow-Origin: http://localhost:5173
# Access-Control-Allow-Headers: authorization
# Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
# Access-Control-Max-Age: 3600
```

```bash
# ② 真请求仍要求 token（401 是对的，证明鉴权没被绕过）
curl -i http://localhost:8090/api/files -H "Origin: http://localhost:5173"
# 期望：401
```

```bash
# ③ 白名单外的来源应被拒绝（不返回 Access-Control-Allow-Origin）
curl -i -X OPTIONS http://localhost:8090/api/files \
  -H "Origin: http://evil.example.com" \
  -H "Access-Control-Request-Method: GET"
# 期望：没有 Access-Control-Allow-Origin 头，或 403
```

三条都符合预期，说明配置正确且没有削弱鉴权。

---

## 五、常见错误

**1. 写了配置但预检还是 401**

大概率是配到了自定义 `CorsWebFilter` 而不是 `globalcors`。`globalcors` 由 `RoutePredicateHandlerMapping` 在路由匹配阶段处理，本来就不进 GlobalFilter 链。自己写的 `WebFilter` 顺序不受控，容易排在鉴权后面。

**2. 浏览器报 `credentials` 相关错误**

前端用了 `fetch(url, { credentials: 'include' })`。本项目不需要，改成 `'same-origin'`（默认值）即可，因为 token 是手动塞 header 的。

**3. 下载文件名还是拿不到**

检查 `exposed-headers` 是否包含 `Content-Disposition`，大小写要和响应头一致。改完记得强刷浏览器（`max-age` 缓存了预检结果）。

**4. 生产环境忘了改来源**

`allowed-origin-patterns` 里如果只留 `localhost`，线上前端会被拒。上线前务必加上真实域名。

---

## 六、可选：用环境变量覆盖（不想改 Nacos 时）

本地 `application.yaml` 写的是：

```yaml
allowed-origin-patterns: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

所以在 `deploy/.env` 或 systemd 的 `EnvironmentFile` 里加一行即可，不用动 Nacos：

```bash
CORS_ALLOWED_ORIGINS=https://pan.example.com,https://www.pan.example.com
```

**优先级**：Nacos 配置 > 环境变量 > 本地默认值。也就是说一旦 Nacos 里写了 `globalcors`，环境变量就不起作用了。二选一，别两边都改。
