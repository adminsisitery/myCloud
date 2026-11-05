# file-task-service 测试用例

> 端口：8091 | 职责：集中定时任务（上传/分片/直传超时清理、分享过期、文件夹打包清理、回收站 30 天清理）
> 生成日期：2026-11-04

## 1. 服务职责

集中式定时任务服务，无业务 HTTP 接口。把散落在各业务服务的超时清理/过期标记任务集中到一处，通过 `@Scheduled` cron 触发，Redisson 分布式锁保证多实例互斥，滚动发布期间与原服务共享锁 key 避免双执行。

## 2. 定时任务清单

| 任务类 | 方法 | Cron | 职责 |
|---|---|---|---|
| `UploadTimeoutTask` | `scanTimeoutTasks` | 每 5 分钟 | 普通上传超时 30min → 归还空间 + 标记失败 |
| `UploadTimeoutTask` | `scanChunkTimeoutTasks` | 每 10 分钟 | 分片上传超时 24h → 清 OSS 残片 + 归还空间 |
| `DirectUploadTimeoutTask` | `cleanupExpired` | 每 5 分钟 | 直传任务超 `expiresAt` → 删临时对象 + 归还空间 |
| `ShareExpireTask` | `markExpiredShares` | 每天 02:00 | 标记 `expire_time < now` 的分享为过期 |
| `FolderDownloadCleanupTask` | `cleanupExpired` | 每 10 分钟 | 清理过期打包 ZIP 与终态任务 |
| `TrashCleanupTask` | `cleanupTrash` | 每天 03:00 | 回收站 30 天文件物理删除 |

## 3. 定时任务测试用例

### 3.1 普通上传超时 `UploadTimeoutTask.scanTimeoutTasks`

**正常用例**
- [ ] TC-TK-001 任务超 30min 且持有未结清预占（`holdsUnsettledReservation`）→ 归还空间 + 标记 FAILED（「上传超时，任务在 X 状态超过30分钟」）
- [ ] TC-TK-002 未超 30min → 跳过（`createdAt > cutoff`）
- [ ] TC-TK-003 已释放空间（`isSpaceReleased`）→ `tryMarkSpaceReleased` 返回 false，跳过（幂等）

**异常/边界用例**
- [ ] TC-TK-004 任务缺少 `userId` → 记 warn 跳过（无法归还空间）
- [ ] TC-TK-005 `releaseSpace` 抛异常 → 回滚「已释放」标记，等待下一轮重试
- [ ] TC-TK-006 任务已是终态（SUCCESS/FAILED/CANCELLED）→ `completeUploadTask` 不再写状态
- [ ] TC-TK-007 分布式锁获取失败（另一实例持锁）→ 跳过本轮

### 3.2 分片上传超时 `UploadTimeoutTask.scanChunkTimeoutTasks`

**正常用例**
- [ ] TC-TK-008 任务超 24h 无活动（`lastActivityTime`/`createdAt`）→ 清理并删除任务
- [ ] TC-TK-009 `storageCompleted=true` → `deleteFile(objectKey)` 删已合并对象
- [ ] TC-TK-010 `storageCompleted=false` → `cancelUpload(ossUploadId, objectKey)` 中止分片上传
- [ ] TC-TK-011 `spacePreAllocated=true` → 置 false + 归还空间 + 删任务

**异常/边界用例**
- [ ] TC-TK-012 任务缺少 `userId`/`fileSize` → 记 warn 跳过
- [ ] TC-TK-013 归还空间失败 → 回滚 `spacePreAllocated=true`，下一轮补偿
- [ ] TC-TK-014 `lastActivityTime == null` → 回退用 `createdAt`

### 3.3 直传超时 `DirectUploadTimeoutTask.cleanupExpired`

**正常用例**
- [ ] TC-TK-015 任务 `expiresAt <= now` → `deleteFile(objectKey)` + `releaseSpace` + `delete(taskId)`
- [ ] TC-TK-016 未过期 → 跳过

**异常/边界用例**
- [ ] TC-TK-017 `expiresAt == null` → 跳过
- [ ] TC-TK-018 单任务锁获取失败（正在完成直传）→ 跳过，防并发
- [ ] TC-TK-019 加锁后任务已过期清理（二次校验 `latest.expiresAt <= now`）→ 清理
- [ ] TC-TK-020 `deleteFile` 抛异常 → `finally` 仍执行 `releaseSpace` + `delete` 任务

### 3.4 分享过期 `ShareExpireTask.markExpiredShares`

- [ ] TC-TK-021 存在 `expire_time < now && is_expired=0` 记录 → 更新 `is_expired=true` + `updatedAt`，返回影响行数
- [ ] TC-TK-022 无过期分享 → `updated=0`
- [ ] TC-TK-023 分布式锁被占用 → 跳过本轮

### 3.5 文件夹打包清理 `FolderDownloadCleanupTask.cleanupExpired`

- [ ] TC-TK-024 SUCCESS 且 `expiresAt <= now` → 删 OSS 临时 zip（objectKey 非空）+ 删任务
- [ ] TC-TK-025 FAILED/CANCELLED 且 `updatedAt` 超 24h → 删任务
- [ ] TC-TK-026 PENDING/PACKAGING/UPLOADING 状态 → 跳过（不清理进行中任务）

### 3.6 回收站清理 `TrashCleanupTask.cleanupTrash`

**正常用例**
- [ ] TC-TK-027 回收站记录 `deletedAt < now-30d` → 物理删除
- [ ] TC-TK-028 非文件夹且 `path` 非空 → 先判断 OSS 对象是否删除
- [ ] TC-TK-029 同一物理对象被多行引用（`countByHash > 1`，秒传/复制）→ 保留 OSS 对象，只删该行 DB 记录
- [ ] TC-TK-030 文件夹 → 不删 OSS 对象，只删 DB 记录

**异常/边界用例**
- [ ] TC-TK-031 `sha256Hash == null` 或 `countByHash <= 1` → 删 OSS 对象
- [ ] TC-TK-032 单条删除异常 → 记 error 后继续处理后续记录（不中断整批）

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `UploadTimeoutTask` | 超时判定、幂等闸（tryMarkSpaceReleased/回滚）、归还失败补偿、终态不重写 |
| `UploadTimeoutTask`（分片） | storageCompleted 分支（deleteFile vs cancelUpload）、spacePreAllocated 回滚、lastActivity 回退 |
| `DirectUploadTimeoutTask` | expiresAt 判定、单任务锁、二次校验、deleteFile 失败仍归还空间 |
| `ShareExpireTask` | 过期条件、更新行数、锁互斥 |
| `FolderDownloadCleanupTask` | SUCCESS 过期删 OSS、终态超 24h 删任务、进行中跳过 |
| `TrashCleanupTask` | 30 天判定、多引用保留 OSS、文件夹不删对象、单条异常不中断 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock mapper / Redis / `UserSpaceClient` / `StorageService`，直接调用 `doXxx` 私有方法（或经 `@Scheduled` 公开入口）。
- **cron 验证**：用 Spring 的 `CronExpression` 断言 cron 表达式（0 */5 / 0 */10 / 0 0 2 / 0 0 3）语义正确；`@Scheduled` 触发时机本身无需测。
- **分布式锁**：mock `RedissonClient.getLock().tryLock()` 返回 true/false 验证互斥与单任务锁；真实多实例互斥需 Redis。
- **跨服务**：`UserSpaceClient.releaseSpace` 调 user-service `/internal/space/*`，单测 mock；端到端需联调 user-service。
