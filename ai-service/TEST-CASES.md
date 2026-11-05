# ai-service 测试用例

> 端口：8088 | 职责：AI 对话 / Agent / RAG / 记忆 / 会话 / 用量统计
> 生成日期：2026-11-04

## 1. 服务职责

AI 能力中心：SSE 流式对话（DeepSeek）、Agent Plan-and-Execute（含动作确认/拒绝/审计/对账）、会话与消息管理、RAG 检索（pgvector 记忆）、管理员 Token 成本统计。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| POST | `/api/ai/chat` | AI 对话（SSE 流式） | 需登录；USER 3 次/s |
| POST | `/api/ai/agent` | Agent 对话（SSE） | 需登录；USER 1 次/s |
| GET | `/api/ai/models` | 可用模型列表 | 需登录 |
| GET | `/api/ai/conversations` | 会话列表 | 需登录 |
| GET | `/api/ai/conversations/page` | 会话列表（分页） | 需登录 |
| POST | `/api/ai/conversations` | 创建会话 | 需登录 |
| PUT | `/api/ai/conversations/{id}` | 重命名会话 | 需登录 |
| DELETE | `/api/ai/conversations/{id}` | 删除会话 | 需登录 |
| GET | `/api/ai/conversations/{id}/messages` | 消息列表 | 需登录 |
| GET | `/api/ai/conversations/{id}/messages/page` | 消息列表（分页） | 需登录 |
| POST | `/api/ai/agent/runs/{id}/resume` | 恢复 Agent 运行（SSE） | 需登录 |
| GET | `/api/ai/agent/runs/{id}` | 查询运行状态 | 需登录 |
| GET | `/api/ai/agent/conversations/{conversationId}/runs` | 运行历史 | 需登录 |
| GET | `/api/ai/agent/conversations/{conversationId}/audit` | 动作审计 | 需登录 |
| POST | `/api/ai/agent/actions/{id}/confirm` | 确认动作 | 需登录 |
| POST | `/api/ai/agent/actions/{id}/reject` | 拒绝动作 | 需登录 |
| POST | `/api/ai/agent/actions/{id}/reconcile` | 对账动作 | 需登录 |
| GET | `/api/ai/agent/actions/{id}/document` | 下载动作生成文档 | 需登录 |
| POST | `/api/ai/agent/runs/{id}/cancel` | 取消运行 | 需登录 |
| GET | `/admin/ai/cost/summary` | 成本总览 | `@SaCheckRole("admin")` |
| GET | `/admin/ai/cost/by-model` | 按模型明细 | admin |
| GET | `/admin/ai/cost/daily` | 按天趋势 | admin |

## 3. 接口测试用例

### 3.1 AI 对话 `POST /api/ai/chat`

**正常用例**
- [ ] TC-AI-001 合法请求 → 返回 `SseEmitter`（`text/event-stream`），按事件流输出回答
- [ ] TC-AI-002 携带会话 ID → 回答关联到会话并落库消息

**异常/边界用例**
- [ ] TC-AI-003 请求体校验失败（`@Valid ChatRequest`，如消息为空）→ 校验错误
- [ ] TC-AI-004 AI 上游（DeepSeek）不可用/超时 → SSE 中返回错误事件
- [ ] TC-AI-005 触发 USER 限流（>3 次/s）→ 限流错误

### 3.2 Agent 对话 `POST /api/ai/agent`

- [ ] TC-AI-006 合法请求 → SSE 流式输出 Agent 执行过程（Plan→Execute→动作）
- [ ] TC-AI-007 动作需用户确认时 → SSE 输出待确认动作，运行挂起等待 resume
- [ ] TC-AI-008 触发限流（>1 次/s）→ 限流错误

### 3.3 模型列表 `GET /api/ai/models`

- [ ] TC-AI-009 返回 `code=200` + `models` 数组 + `defaultModel`

### 3.4 会话管理 `/api/ai/conversations`

**正常用例**
- [ ] TC-AI-010 创建会话 → 返回 `ConversationVO`（新会话 ID）
- [ ] TC-AI-011 会话列表 → 返回当前用户会话（不含他人会话）
- [ ] TC-AI-012 分页列表 → `page`/`size` 生效（默认 1/20）
- [ ] TC-AI-013 重命名 → 成功，`title` 更新
- [ ] TC-AI-014 删除会话 → 成功，会话及消息删除

**异常/边界用例**
- [ ] TC-AI-015 重命名他人会话 → 无权限/不存在错误
- [ ] TC-AI-016 删除他人会话 → 无权限/不存在错误
- [ ] TC-AI-017 重命名请求校验失败（`@Valid RenameRequest`）→ 校验错误

### 3.5 消息列表 `/api/ai/conversations/{id}/messages`

- [ ] TC-AI-018 本人会话 → 返回消息列表
- [ ] TC-AI-019 分页 → 按时间倒序，默认 size=50
- [ ] TC-AI-020 他人会话 → 无权限/不存在

### 3.6 Agent 运行控制 `/api/ai/agent/runs/{id}/...`

- [ ] TC-AI-021 查询运行状态 → 返回 `run` + `actions`
- [ ] TC-AI-022 查询运行历史 → 返回该会话的运行列表
- [ ] TC-AI-023 恢复运行（resume，带 `answer` ≤2000 字）→ SSE 继续执行
- [ ] TC-AI-024 取消运行 → 返回 `{status: "cancel_requested"}`
- [ ] TC-AI-025 resume `answer` 超 2000 字 → 校验错误
- [ ] TC-AI-026 查询他人运行 → 无权限/不存在

### 3.7 Agent 动作 `/api/ai/agent/actions/{id}/...`

- [ ] TC-AI-027 确认动作（confirm）→ 动作状态更新为已确认
- [ ] TC-AI-028 拒绝动作（reject）→ 动作状态更新为已拒绝
- [ ] TC-AI-029 对账（reconcile）→ 返回对账后的动作
- [ ] TC-AI-030 审计列表 → 返回该会话动作审计记录
- [ ] TC-AI-031 下载文档 → 返回字节流，`Content-Disposition=attachment`
- [ ] TC-AI-032 下载文档但 `content=null` → 404

### 3.8 成本统计 `/admin/ai/cost/**`

- [ ] TC-AI-033 admin + `summary` → 返回总 token/费用/调用次数/平均延迟
- [ ] TC-AI-034 admin + `by-model` → 按模型+场景分组明细
- [ ] TC-AI-035 admin + `daily` → 按天趋势
- [ ] TC-AI-036 非 admin 角色 → 403/无权限（`@SaCheckRole` 拦截）
- [ ] TC-AI-037 `range` 参数非法 → 错误或默认值

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `AiChatService.sendMessage` | 上游调用失败降级；SSE 事件顺序；消息落库 |
| `AiChatService.sendAgentMessage` | Plan-Execute 循环、动作挂起/恢复、取消信号 |
| `AiConversationService.getConversationList` | 会话按 userId 隔离 |
| `AiConversationService.deleteConversation` | 他人会话无权限；级联删除消息 |
| `AgentActionService.audit/confirm/reject/reconcile` | 动作状态机、归属校验 |
| `AiCostService.getSummary/getByModel/getDaily` | 时间范围解析、聚合统计 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock 模型客户端 / LLM / RAG store，不依赖中间件。
- **接口测试（Chat/Agent SSE）**：需 mock LLM 上游（DeepSeek），用 `MockMvc` + `asyncDispatch` 或 WebTestClient 消费 SSE；DB 用 H2。
- **RAG/记忆**：需 PostgreSQL + pgvector（`file_segment` 表，embedding 1536 维）或 mock `PgVectorEmbeddingStore`。
- **成本统计**：需 admin 登录态（Sa-Token 角色）。
