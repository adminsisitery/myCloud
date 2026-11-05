# user-service 测试用例

> 端口：8081 | 职责：用户/登录/注册/GitHub OAuth/空间配额
> 生成日期：2026-11-04

## 1. 服务职责

用户认证与账户中心：注册、登录、登出、邮箱验证码、GitHub 第三方登录、密码修改、用户中心（空间配额）查询，以及供兄弟服务调用的空间预占/释放内部接口。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| POST | `/api/user/login` | 登录，返回 Sa-Token | IP 5 次/s |
| POST | `/api/user/register` | 注册（含验证码校验） | IP 2 次/s |
| POST | `/api/user/verification-code` | 发邮箱验证码 | IP 1 次/s |
| POST | `/api/user/logout` | 当前用户登出 | 需登录，USER 5 次/s |
| POST | `/api/user/forceLogout/{userId}` | 管理员强制下线 | `@SaCheckRole("admin")` |
| POST | `/api/user/changeRole` | 管理员改角色 | `@SaCheckRole("admin")` |
| GET | `/api/user/info` | 查当前用户云盘空间信息 | 需登录 |
| POST | `/api/user/change-password` | 修改密码 | 需登录 |
| GET | `/api/auth/github/login` | 跳转 GitHub 授权 | IP+全局 5 次/min |
| GET | `/api/auth/github/callback` | GitHub 回调 | IP+全局 5 次/min |
| POST | `/internal/space/reserve` | 预占空间 | 内网（网关无路由） |
| POST | `/internal/space/release` | 释放空间 | 内网 |
| POST | `/internal/space/add` | 恢复空间（回收站恢复） | 内网 |
| GET | `/internal/space/info` | 查空间用量 | 内网 |

## 3. 接口测试用例

### 3.1 登录 `POST /api/user/login`

**正常用例**
- [ ] TC-U-001 正确用户名+密码 → 返回 `code=200`，data 为 Sa-Token 字符串（非空）
- [ ] TC-U-002 登录成功后，Redis 中失败计数被重置（`loginFailRedis.reset` 生效）

**异常用例**
- [ ] TC-U-003 用户名不存在 → 返回 `USER_NOT_FOUND`（与密码错误区分）
- [ ] TC-U-004 密码错误 → 返回 `INVALID_PASSWORD`，且失败计数 +1
- [ ] TC-U-005 连续失败达阈值 → 返回 `ACCOUNT_LOCKED`（账户渐进退避/硬锁）
- [ ] TC-U-006 用户 `status != 1`（禁用）→ 返回 `ACCOUNT_DISABLED`
- [ ] TC-U-007 触发 IP 限流（>5 次/s）→ 返回限流错误

**参数校验用例**
- [ ] TC-U-008 `username` 为空 → 返回校验错误「用户名不能为空」
- [ ] TC-U-009 `password` 为空 → 返回校验错误「密码不能为空」

### 3.2 注册 `POST /api/user/register`

**正常用例**
- [ ] TC-U-010 合法参数 + 正确验证码 → 返回成功；DB 新增 user、user_center（默认配额）、user_role 记录
- [ ] TC-U-011 注册成功后，该邮箱的验证码被删除（不可复用）

**异常用例**
- [ ] TC-U-012 用户名已存在 → 返回 `USERNAME_EXISTS`
- [ ] TC-U-013 邮箱已存在 → 返回 `EMAIL_EXISTS`
- [ ] TC-U-014 验证码错误 → 返回 `VERIFICATION_CODE_ERROR`
- [ ] TC-U-015 验证码过期/未发送 → 返回 `VERIFICATION_CODE_ERROR`

**参数校验用例**
- [ ] TC-U-016 用户名长度 <3 或 >20 → 校验错误「用户名长度必须在3到20个字符之间」
- [ ] TC-U-017 密码长度 <6 或 >20 → 校验错误
- [ ] TC-U-018 密码纯数字或纯字母（不含字母+数字组合）→ 校验错误「密码必须是数字和英文组合」
- [ ] TC-U-019 邮箱格式非法 → 校验错误「邮箱格式不正确」
- [ ] TC-U-020 验证码为空 → 校验错误「验证码不能为空」

### 3.3 发送验证码 `POST /api/user/verification-code`

**正常用例**
- [ ] TC-U-021 未注册邮箱 → 返回成功；Redis 写入验证码（6 位）

**异常用例**
- [ ] TC-U-022 邮箱已注册 → 返回 `EMAIL_EXISTS`
- [ ] TC-U-023 邮件发送失败（Mail 不可用）→ 返回 `EMAIL_SEND_ERROR`
- [ ] TC-U-024 邮箱格式非法 → 校验错误

### 3.4 登出 `POST /api/user/logout`

- [ ] TC-U-025 已登录 → 返回成功，Sa-Token 会话失效，再调需登录接口返回未登录
- [ ] TC-U-026 未登录 → 返回 `USER_NOT_LOGGED_IN`

### 3.5 强制下线 `POST /api/user/forceLogout/{userId}`

- [ ] TC-U-027 admin 角色 → 返回成功，目标 userId 会话失效
- [ ] TC-U-028 非 admin 角色 → 返回 403/无权限（Sa-Token 拦截）
- [ ] TC-U-029 未登录 → 返回未登录

### 3.6 修改角色 `POST /api/user/changeRole`

- [ ] TC-U-030 admin 角色 + 合法 userRole → 返回成功，DB 角色更新
- [ ] TC-U-031 非 admin 角色 → 返回无权限

### 3.7 查询用户中心信息 `GET /api/user/info`

- [ ] TC-U-032 已登录 → 返回 `UserCenter`（含 totalSpace/usedSpace）
- [ ] TC-U-033 未登录 → 返回未登录

### 3.8 修改密码 `POST /api/user/change-password`

- [ ] TC-U-034 旧密码正确 + 新密码合法 → 返回成功，新密码可登录
- [ ] TC-U-035 旧密码错误 → 返回错误（不修改密码）
- [ ] TC-U-036 未登录 → 返回未登录

### 3.9 GitHub OAuth（需 mock 第三方）

- [ ] TC-U-037 `login` 未配置 oauth.github 凭据 → 重定向前端登录页 + `error=GitHub 登录未配置`（降级，不 500）
- [ ] TC-U-038 `callback` 授权失败（authUser==null）→ 重定向 `/login`
- [ ] TC-U-039 新 GitHub 用户回调 → 自动注册（占位密码 `{GITHUB_LOGIN}`）+ 建 user_center（10MB）+ 重定向 `oauth-success?token=...`
- [ ] TC-U-040 已存在 GitHub 用户回调 → 不重复建号，头像变更时更新，重定向携带 token
- [ ] TC-U-041 触发限流（>5 次/min）→ 走降级重定向 `/login?error=请求太频繁`

### 3.10 空间内部接口 `/internal/space/**`

- [ ] TC-U-042 `reserve` 空间充足 → 返回 true，usedSpace 增加
- [ ] TC-U-043 `reserve` 空间不足 → 返回 false，usedSpace 不变
- [ ] TC-U-044 `release` → 返回成功，usedSpace 减少
- [ ] TC-U-045 `add`（恢复）→ usedSpace 增加（不检查剩余空间）
- [ ] TC-U-046 `info` 用户存在 → 返回 usedSpace/totalSpace
- [ ] TC-U-047 `info` 用户不存在 → 返回 404「用户空间信息不存在」

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `UserServiceImpl.login` | 用户名不存在/密码错误/账户禁用/锁定，四条失败路径 + 成功路径；验证 `loginFailRedis` 记录与重置调用次数 |
| `UserServiceImpl.register` | 事务回滚：用户名/邮箱/验证码任一失败时不落库；成功时验证三张表写入 |
| `UserServiceImpl.sendVerificationCode` | 邮箱已存在拦截；`EmailUtil` 返回 false 时抛 `EMAIL_SEND_ERROR` 且不写 Redis |
| `UserServiceImpl.forceLogout` | 未登录时抛 `USER_NOT_LOGGED_IN` |
| `UserCenterServiceImpl.changePassword` | 旧密码错误分支；新密码加密落库 |
| `UserCenterMapperServiceImpl.reserveSpace` | 并发扣减不超卖（空间不足时返回 false 且 usedSpace 不越界） |

## 5. 测试环境说明

- **纯单测**（DTO 校验、Service 逻辑）：不依赖中间件，H2 或 Mockito。
- **接口测试**（`@SpringBootTest` + `MockMvc` / TestRestTemplate）：需 Redis（Sa-Token 会话 + 验证码/失败计数）；DB 可用 H2 替代 MySQL。
- **GitHub OAuth**：需 `MockWebServer` 模拟 GitHub 授权端点，或直接测未配置降级分支。
- **内部接口鉴权**：`/internal/**` 网关无路由，测试时直连 8081 验证，外部经网关应 404。
