# pan-gateway 测试用例

> 端口：8090 | 职责：Spring Cloud Gateway 网关（HTTP + WebSocket 路由、Sa-Token 登录校验）
> 生成日期：2026-11-04

## 1. 服务职责

统一入口网关（WebFlux 响应式）。职责：

1. **路由转发**：按 Nacos 托管配置 `pan-gateways` 把请求转发到下游微服务；上传/下载/预览/搜索的精确路由优先于 `/api/files/**` 管理路由。
2. **统一鉴权**：`MyGlobalFilter` 用 Sa-Token 校验登录，未登录返回 401，已登录则把 `loginId` 写入 `UserId` 请求头（覆盖并移除原头）透传给下游。
3. **白名单放行**：`pan-exclude-paths` 配置的路径（如登录/注册/OAuth 回调）跳过鉴权。

## 2. 全局过滤器与配置

| 组件 | 说明 |
|---|---|
| `MyGlobalFilter` | `GlobalFilter` + `Ordered(HIGHEST_PRECEDENCE)`，校验登录并注入 `UserId` 头 |
| `AuthProperties` | `pan.user.exclude-paths`，AntPathMatcher 匹配白名单 |
| 路由配置 | Nacos `pan-gateways`（HTTP + WebSocket 路由） |

## 3. 网关鉴权测试用例

### 3.1 白名单放行

- [ ] TC-GW-001 请求路径命中 `exclude-paths`（如 `/api/user/login`、GitHub OAuth 回调）→ `isExcluded` 返回 true，直接放行，不校验登录
- [ ] TC-GW-002 未命中白名单 → 继续登录校验
- [ ] TC-GW-003 Ant 通配符匹配（如 `/api/**/public/**`）→ 正确匹配

### 3.2 登录校验

**正常用例**
- [ ] TC-GW-004 携带合法 Sa-Token → `checkLogin` 通过，`getLoginId` 拿到 userId
- [ ] TC-GW-005 已登录 → 请求头移除原 `UserId` 并 `set` 为 loginId 后透传下游
- [ ] TC-GW-006 下游收到 `UserId` 头为字符串形式的 loginId

**异常用例**
- [ ] TC-GW-007 未携带 token / token 无效 → 抛 `NotLoginException`，返回 401（`UNAUTHORIZED`），不转发
- [ ] TC-GW-008 401 响应体为空（`response.setComplete()`），不泄露下游信息

### 3.3 过滤器顺序

- [ ] TC-GW-009 `getOrder()` 返回 `Ordered.HIGHEST_PRECEDENCE`，鉴权先于路由转发执行

## 4. 路由转发测试用例

- [ ] TC-GW-010 `/api/files/search/**` 精确路由优先命中 file-search-service（不落到 `/api/files/**` 管理路由）
- [ ] TC-GW-011 `/api/files/preview/**` 命中 file-preview-service
- [ ] TC-GW-012 `/api/files/**` 管理类请求命中 file-manage-service
- [ ] TC-GW-013 `/ws/message` 命中 file-async-service（WebSocket 路由）
- [ ] TC-GW-014 `/api/ai/**` SSE 流式接口命中 ai-service
- [ ] TC-GW-015 未匹配任何路由 → 返回 404

## 5. 关键单元测试点（Mockito / WebTestClient）

| 目标 | 测试点 |
|---|---|
| `MyGlobalFilter.filter` | 白名单放行、未登录 401、已登录注入 UserId 头、移除原 UserId 头 |
| `AuthProperties.isExcluded` | AntPathMatcher 匹配（精确/通配符）、exclude-paths 为空时返回 false |
| `AuthProperties.setExcludePaths` | 传入 null 归一化为空列表 |

## 6. 测试环境说明

- **单元测试**：`WebTestClient` + `@SpringBootTest` 模拟完整网关；或直接构造 `ServerWebExchange`（`MockServerWebExchange`）+ mock `GatewayFilterChain` 验证过滤器行为。
- **登录态**：Sa-Token 依赖共享 Redis 中已签发 token；单测可 mock `StpUtil` 或用真实 token + 测试 Redis。
- **路由转发**：路由定义在 Nacos `pan-gateways`，本地测试需 `spring.cloud.nacos.config` 指向测试 Nacos 或本地 mock 路由定义。
- **端到端**：网关 + 目标服务 + Redis 联调，验证 token 签发（user-service）→ 网关校验 → 下游 `UserId` 头注入全链路。
