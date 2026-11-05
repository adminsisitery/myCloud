# file-async-service 测试用例

> 端口：8086 | 职责：MQ 消费端（ES/PG 索引同步、操作日志、用量通知）+ WebSocket 推送 + 文件夹打包执行
> 生成日期：2026-11-04

## 1. 服务职责

异步处理中心，无业务 HTTP 接口，核心为两类能力：

1. **MQ 消费者**（`@RabbitListener`）：ES 索引同步、PG 向量同步、操作日志落库、用量通知（WebSocket + 邮件）、文件夹打包执行。
2. **WebSocket 服务端**：`/ws/message` 握手校验 Sa-Token，向指定用户实时推送用量消息。

重试策略统一 `@Retryable`（3 次 + 退避），耗尽后由 Broker DLX 转入死信队列 `dlx:queue`。

## 2. 消费端清单（队列）

| 队列 | 消费者 | 说明 |
|---|---|---|
| `ES.insert.queue` / `ES.delete.queue` / `ES.update.queue` | `ESSyncConsumer` | ES 索引增删改同步 |
| `pg.insert.queue` / `pg.delete.queue` | `PGSyncConsumer` | PG 向量索引同步（`@ConditionalOnBean(EmbeddingStore)`） |
| `log.queue` | `OperationLogConsumer` | 操作日志落库（Redis 幂等） |
| `usage:queue` | `UsageNotificationConsumer` | 用量通知（WebSocket + 邮件） |
| `folder.download.queue` | `FolderDownloadConsumer` | 文件夹打包执行（并发 1-2） |

## 3. 消费端测试用例

### 3.1 ES 索引同步 `ESSyncConsumer`

**正常用例**
- [ ] TC-AS-001 INSERT/UPDATE 消息 + 文件存在且未删 → `FileSearchService.save(document)`，索引文件名含后缀（`filename.suffix`，文件夹无后缀）
- [ ] TC-AS-002 DELETE 消息 + 文件存在且未删 → `deletion=true`，跳过保存（不删索引）
- [ ] TC-AS-003 消息指向已删除文件（`isDeleted=true`）或 DB 无此文件 → `del(fileId)` 删索引并返回

**异常/边界用例**
- [ ] TC-AS-004 `message == null` 或 `id == null` → 抛 `IllegalArgumentException("索引消息缺少文件ID")`（触发重试）
- [ ] TC-AS-005 分布式锁获取失败（10s 未拿到）→ 抛 `IllegalStateException("索引繁忙，请重试")`
- [ ] TC-AS-006 消息 `userId` 与 DB `userId` 不一致 → 跳过（`log.warn` 归属不一致），不写索引
- [ ] TC-AS-007 获取锁被中断（InterruptedException）→ 恢复中断标志 + 抛「索引任务已中断」
- [ ] TC-AS-008 处理抛异常 → `@Retryable` 重试 3 次（间隔 3s），耗尽进死信

### 3.2 PG 向量同步 `PGSyncConsumer`

**正常用例**
- [ ] TC-AS-009 INSERT 消息 + 文件有效且归属一致 → 分片（`documentSplitter`）→ embedding → 先 `removeAll` 旧向量再 `addAll` 新向量，metadata 带 `fileId/userId/fileName`
- [ ] TC-AS-010 `rebuild=true` 且 text 缺失 → `indexRebuilder.extract(file)` 提取文本，完成后 `restoreKeywords`
- [ ] TC-AS-011 DELETE 消息 + 文件已删除/归属变化 → `removeAll(scope)` 删向量
- [ ] TC-AS-012 DELETE 消息 + 文件仍有效（如回收站已恢复）→ 跳过删除（防误删）

**异常/边界用例**
- [ ] TC-AS-013 `message == null` → 直接返回（不处理）
- [ ] TC-AS-014 `fileId`/`userId` 缺失 → 抛「索引身份缺失」
- [ ] TC-AS-015 文件是文件夹 → 跳过（不建向量）
- [ ] TC-AS-016 `contentHash` 与 DB `sha256Hash` 不一致 → 跳过过期消息
- [ ] TC-AS-017 text 缺失且非 rebuild → 抛「索引文本缺失」
- [ ] TC-AS-018 text 为空白 → `segments` 为空，不调 embedding，仅清空旧向量
- [ ] TC-AS-019 长耗时 embedding 后文件归属/内容已变 → 二次校验拦截，不覆盖新索引
- [ ] TC-AS-020 无 `EmbeddingStore` bean → `PGSyncConsumer` 不装配（`@ConditionalOnBean`）

### 3.3 操作日志 `OperationLogConsumer`

**正常用例**
- [ ] TC-AS-021 首次消息 + 合法 `msgId` → 映射为 `OperationLog` 落库
- [ ] TC-AS-022 重复消息（同 `msgId`）→ `MessageDedupService.tryConsume` 返回 false，跳过不重复写

**异常/边界用例**
- [ ] TC-AS-023 `message == null` → 直接返回
- [ ] TC-AS-024 `msgId` 为空 → 去重直接放行（不幂等，按实现确认是否应拦截）

### 3.4 用量通知 `UsageNotificationConsumer`

**正常用例**
- [ ] TC-AS-025 合法消息 + 用户存在且有邮箱 + 当日未通知 → WebSocket `sendMsg` + `EmailUtil.sendUsage` 发送，写入当日去重标记（TTL 1 天）
- [ ] TC-AS-026 当日已通知（Redis 去重 bucket 存在）→ 跳过，不发邮件

**异常/边界用例（不可恢复 → 拒收不重投，进死信）**
- [ ] TC-AS-027 `userId == null` → 抛 `AmqpRejectAndDontRequeueException`（直接死信，不重试）
- [ ] TC-AS-028 用户不存在（`selectById` 返回 null）→ `AmqpRejectAndDontRequeueException`
- [ ] TC-AS-029 用户邮箱为空/空白 → `AmqpRejectAndDontRequeueException`

**异常/边界用例（可恢复 → 重试 3 次）**
- [ ] TC-AS-030 查用户 DB 异常 → 抛 `IllegalStateException` 触发重试
- [ ] TC-AS-031 Redis 去重检查异常 → 抛 `IllegalStateException` 触发重试
- [ ] TC-AS-032 WebSocket 推送异常 → 不阻断，继续发邮件（`log.warn`）
- [ ] TC-AS-033 邮件发送失败 → 不写去重标记，`log.warn`，消息正常 ACK
- [ ] TC-AS-034 重试 3 次耗尽 → `@Recover` 兜底记日志，异常传播进死信

### 3.5 文件夹打包 `FolderDownloadConsumer` + `FolderDownloadTaskServiceImpl`

**正常用例**
- [ ] TC-AS-035 收到 `taskId` + 任务 PENDING → 加锁 → 状态 `PACKAGING` → 打包 ZIP → 上传 OSS → 状态 `SUCCESS`（progress=1.0、objectKey、expiresAt=+24h）
- [ ] TC-AS-036 打包进度每满 1MB → 更新 `processedBytes`/`progress`（最大 0.9）

**异常/边界用例**
- [ ] TC-AS-037 加锁失败（并发消费）→ 直接返回不处理
- [ ] TC-AS-038 任务不存在 / 已终态（SUCCESS/CANCELLED）/ 已请求取消 → 直接返回
- [ ] TC-AS-039 打包过程中收到取消 → 抛 `CancellationException`，删临时对象，状态 `CANCELLED`
- [ ] TC-AS-040 上传 OSS 后收到取消 → 删已上传临时对象 + 置 `CANCELLED`
- [ ] TC-AS-041 打包/上传抛异常 → 删临时对象，状态 `FAILED`（message=「打包失败: ...」），再抛 RuntimeException 触发重试
- [ ] TC-AS-042 临时文件删除失败 → 不阻断（`log.warn`）
- [ ] TC-AS-043 打包任务总量为 0 → progress 直接置 0.9（防除零）

## 4. WebSocket `/ws/message` 测试用例

- [ ] TC-AS-044 携带合法 `token` 握手 → `SaTokenConfigurator` 解析 `userId` 存入 userProperties，`onOpen` 建立连接并注册 SESSION
- [ ] TC-AS-045 不携带 token 或 token 无效 → `userId=null`，`onOpen` 关闭连接
- [ ] TC-AS-046 同一 userId 重复连接 → SESSIONS 覆盖旧 session
- [ ] TC-AS-047 `onClose` → 从 SESSIONS 移除该 userId
- [ ] TC-AS-048 `sendMsg(userId, msg)` + session 在线且 open → `asyncRemote.sendText` 推送
- [ ] TC-AS-049 `sendMsg` + 目标不在线/已关闭 → 静默不推送（不抛异常）

## 5. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `ESSyncConsumer.synchronize` | 消息缺 ID、锁失败、文件已删→del、deletion 跳过、归属不一致跳过、文件名后缀拼接 |
| `PGSyncConsumer.handleInsert` | ownedLive 校验、contentHash 校验、空文本、embedding 后二次校验、removeAll+addAll、metadata |
| `PGSyncConsumer.handleDelete` | 文件仍有效跳过 / 已删则 removeAll |
| `OperationLogConsumer.handleLog` | msgId 幂等去重、实体字段映射 |
| `UsageNotificationConsumer.check` | 四类不可恢复异常、两类可恢复异常、Redis 当日去重、邮件失败不标记 |
| `MessageDedupService.tryConsume` | msgId 空放行、SETNX 首次 true/重复 false、TTL 24h |
| `FolderDownloadTaskServiceImpl.process` | 加锁、终态跳过、取消（打包中/上传后）、失败清理、进度更新（0.9 封顶/除零） |
| `FolderDownloadTaskServiceImpl.cleanupExpired` | SUCCESS 过期删 OSS、FAILED/CANCELLED 超 24h 删任务 |
| `OssStorageServiceImpl` | `OSS_DISABLED` 分支、流关闭连带关 OSSClient、上传/删除失败 |

## 6. 测试环境说明

- **纯单测**：Mockito，mock `RabbitListener` 触发直接调用 consumer 方法（或 `@MockBean` 依赖），不依赖中间件。
- **消息体反序列化**：消费者与生产者共享 `pan-common` 消息类（同 FQCN），用 Jackson `__TypeId__` 验证反序列化。
- **集成测试（可选）**：RabbitMQ + Redisson + PostgreSQL(pgvector) + Elasticsearch + OSS mock；`Testcontainers` 起 RabbitMQ/Redis/PG。
- **WebSocket**：用真实 WebSocket 客户端连 `/ws/message?token=xxx`，校验握手鉴权与推送；Sa-Token 需共享 Redis 中已签发 token。
- **DLX**：集成测试验证重试耗尽消息最终进 `dlx:queue`。
