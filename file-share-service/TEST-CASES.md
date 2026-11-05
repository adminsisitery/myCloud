# file-share-service 测试用例

> 端口：8085 | 职责：分享创建/列表/取消、密码与 Token 校验、分享内容下载
> 生成日期：2026-11-04

## 1. 服务职责

文件分享中心：创建分享链接（生成分享码 + 提取码）、查询当前用户分享列表、取消分享、密码/Token 双通道校验访问分享内容、分享文件（字节流）与分享文件夹（ZIP 打包）下载，以及基于 Redis 延迟队列 + 定时任务兜底的分享过期标记。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| POST | `/api/shares` | 创建文件分享，返回分享码与提取码 | 需登录；USER+IP 5 次/s |
| GET | `/api/shares` | 当前用户所有未过期分享列表 | 需登录；USER 10 次/s |
| DELETE | `/api/shares/{shareCode}` | 取消分享 | 需登录；USER 5 次/s |
| POST | `/api/shares/{shareCode}` | 访问分享内容（Cookie Token 免密优先，降级密码） | 公开；IP 10 次/s |
| POST | `/api/shares/{shareCode}/verification` | 验证密码并下发分享 Token Cookie | 公开；IP 3 次/s |
| GET | `/api/shares/{shareCode}/content` | 下载分享内容（单文件字节流 / 文件夹 ZIP） | 公开；IP 2 次/s |

## 3. 接口测试用例

### 3.1 创建分享 `POST /api/shares`

**正常用例**
- [ ] TC-SH-001 合法 `fileId` + 未来 `expireTime` + 无密码 → 返回 `code=200`，data 含 32 位无横线 `shareCode`（UUID 去横线）、`hasPassword=false`；DB 新增 `t_share_record`，记录 ID 加入 Redis 延迟队列
- [ ] TC-SH-002 合法 `fileId` + 未来 `expireTime` + 合法 6 位密码 → `hasPassword=true`，且分享者本人可看到 `password`
- [ ] TC-SH-003 分享文件夹（`isFolder=true`）→ 返回 `isFolder=true`、`fileSize=0`

**异常用例**
- [ ] TC-SH-004 `fileId` 不存在 → 返回 `FILE_NOT_FOUND`(404)
- [ ] TC-SH-005 分享他人文件（`fileInfo.userId != 当前用户`）→ 返回 `NO_SHARE_PERMISSION`(403)
- [ ] TC-SH-006 触发限流（USER/IP >5 次/s）→ 返回限流错误

**边界用例**
- [ ] TC-SH-007 `expireTime` 等于当前时间（`@FutureOrPresent` 允许）→ 创建成功，但很快过期
- [ ] TC-SH-008 `password` 为 null → 按无密码分享处理
- [ ] TC-SH-009 `password` 为纯空白（如 `"    "`）→ `normalizePassword` 归一化为 null，按无密码分享处理

**参数校验用例**
- [ ] TC-SH-010 `fileId` 为 null → 校验错误「文件ID不能为空」
- [ ] TC-SH-011 `expireTime` 为 null → 校验错误「过期时间不能为空」
- [ ] TC-SH-012 `expireTime` 早于当前时间 → 校验错误「过期时间不能早于当前时间」
- [ ] TC-SH-013 密码格式非法（5 位 / 纯数字 / 纯字母）→ 校验错误「密码必须是6位数字和字母组合」

### 3.2 分享列表 `GET /api/shares`

**正常用例**
- [ ] TC-SH-014 已登录且存在未过期分享 → 返回列表，元素含 `shareCode`/`expireTime`/`hasPassword`/`filename`/`fileSize`/`visitCount`
- [ ] TC-SH-015 普通文件分享 → `filename` 为「文件名 + 后缀」（`filename + "." + fileSuffix`）
- [ ] TC-SH-016 文件夹分享 → `filename` 为文件夹名、`fileSize=0`
- [ ] TC-SH-017 无任何分享 → 返回空列表

**异常用例**
- [ ] TC-SH-018 分享记录关联的文件已删除/不存在 → 跳过该条，不出现在返回列表
- [ ] TC-SH-019 已过期分享（`isExpired=true`）→ 不出现在列表（查询条件 `is_expired=false`）
- [ ] TC-SH-020 触发 USER 限流（>10 次/s）→ 返回限流错误

### 3.3 取消分享 `DELETE /api/shares/{shareCode}`

**正常用例**
- [ ] TC-SH-021 本人分享 + 正确 `shareCode` → 返回成功；DB 记录删除、延迟队列移除、Redis 分享 Token 删除
- [ ] TC-SH-022 取消后再次访问该分享 → 返回 `SHARE_NOT_FOUND`

**异常用例**
- [ ] TC-SH-023 `shareCode` 不存在 → 返回 `SHARE_NOT_FOUND`(404)
- [ ] TC-SH-024 取消他人分享（`findByUserIdAndShareCode` 查无）→ 返回 `SHARE_NOT_FOUND`
- [ ] TC-SH-025 触发 USER 限流（>5 次/s）→ 返回限流错误

### 3.4 访问分享内容 `POST /api/shares/{shareCode}`

**正常用例**
- [ ] TC-SH-026 无密码分享 + 无 Cookie Token → 直接访问成功，`visitCount` +1
- [ ] TC-SH-027 有密码分享 + Cookie 中携带有效 Token（`share_token_{shareCode}`）→ 免密访问成功
- [ ] TC-SH-028 Token 失效 → 降级为密码验证，密码正确 → 访问成功
- [ ] TC-SH-029 有密码分享 + 正确密码（无 Token）→ 访问成功，`visitCount` +1

**异常用例**
- [ ] TC-SH-030 `shareCode` 不存在 → 返回 `SHARE_NOT_FOUND`(404)
- [ ] TC-SH-031 分享已过期（`isExpired=true` 或 `expireTime` 早于当前时间）→ 返回 `SHARE_EXPIRED`(410)
- [ ] TC-SH-032 密码错误 → 返回 `INVALID_PASSWORD`(401)
- [ ] TC-SH-033 有密码但未传密码（`password=null`）→ 返回 `MISSING_PASSWORD`(401)
- [ ] TC-SH-034 分享关联文件已删除 → 返回 `FILE_NOT_FOUND`(404)
- [ ] TC-SH-035 Token 访问抛出异常降级后密码也错误 → 返回 `INVALID_PASSWORD`

**边界用例**
- [ ] TC-SH-036 Token 有效但分享已过期 → 走 Token 通道 `accessShareByToken`（该通道不校验过期时间），需按真实行为验证并确认是否符合产品预期
- [ ] TC-SH-037 Token 有效但分享关联文件已删除 → `accessShareByToken` 抛 `FILE_NOT_FOUND`，Controller 捕获后降级为密码验证

### 3.5 密码校验并下发 Token `POST /api/shares/{shareCode}/verification`

**正常用例**
- [ ] TC-SH-038 正确密码 → 访问成功，返回分享 VO，同时 `Set-Cookie` 下发 `share_token_{shareCode}`（`HttpOnly=false`、`Max-Age=86400`、`SameSite=Lax`、`Path=/`）
- [ ] TC-SH-039 无密码分享 + 任意 6 位密码 → 访问成功（`accessShare` 不校验密码），并下发 Token

**异常用例**
- [ ] TC-SH-040 `shareCode` 不存在 → 返回 `SHARE_NOT_FOUND`(404)
- [ ] TC-SH-041 密码错误 → 返回 `INVALID_PASSWORD`(401)
- [ ] TC-SH-042 分享已过期 → 返回 `SHARE_EXPIRED`(410)
- [ ] TC-SH-043 触发 IP 限流（>3 次/s）→ 返回限流错误

**参数校验用例**
- [ ] TC-SH-044 `password` 为空 → 校验错误「密码不能为空」
- [ ] TC-SH-045 `password` 长度非 6（5 位或 7 位）→ 校验错误「密码长度必须为6位」

### 3.6 分享内容下载 `GET /api/shares/{shareCode}/content`

**正常用例**
- [ ] TC-SH-046 单文件分享 + 有效 Token（无密码分享 Token 可为 null）→ 返回 `application/octet-stream` 字节流，`Content-Disposition` 携带文件名
- [ ] TC-SH-047 文件夹分享 → 走 `folderShareService.downLoadSharedFolder`，返回 `application/zip`，ZIP 内根目录为文件夹名

**异常用例**
- [ ] TC-SH-048 `shareCode` 不存在 → 返回 `SHARE_NOT_FOUND`(404)
- [ ] TC-SH-049 有密码分享 + Token 无效/缺失 → 返回 `INVALID_TOKEN`(401)
- [ ] TC-SH-050 分享已过期 → 返回 `SHARE_EXPIRED`(410)（下载路径校验过期）
- [ ] TC-SH-051 原文件已删除 → 返回 `FILE_NOT_FOUND`(404)
- [ ] TC-SH-052 单文件下载时存储读取失败 → 返回 `FILE_DOWNLOAD_FAILED`(500)
- [ ] TC-SH-053 触发 IP 限流（>2 次/s）→ 返回限流错误

**边界用例**
- [ ] TC-SH-054 无密码分享下载，Token 为 null → 成功（`getFileInfoByShare`/`downloadSharedFile` 对无密码分享不校验 Token）
- [ ] TC-SH-055 Token 有效但分享已过期 → `getFileInfoByShare` 抛 `SHARE_EXPIRED`（与 3.4 的 Token 访问通道行为不同）
- [ ] TC-SH-056 文件夹内存在重名文件 → ZIP 条目自动追加 `(n)` 去重

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `FileShareServiceImpl.createShare` | 文件不存在 `FILE_NOT_FOUND` / 无分享权限 `NO_SHARE_PERMISSION` / 成功路径；验证 `shareMapper.save`、`shareQueueRedis.addToDelayedQueue` 调用；密码空白归一化为 null |
| `FileShareServiceImpl.accessShare` | `SHARE_NOT_FOUND` / `SHARE_EXPIRED` / `MISSING_PASSWORD` / `INVALID_PASSWORD` / `FILE_NOT_FOUND` 五条失败路径 + 成功路径；成功时验证 `visitedPlus1` 调用一次 |
| `FileShareServiceImpl.validateShareToken` | 分享不存在返回 false；无密码分享直接返回 true；有密码分享委托 `shareTokenRedis.validateToken` |
| `FileShareServiceImpl.accessShareByToken` | Token 无效抛 `INVALID_TOKEN`；记录不存在抛 `SHARE_NOT_FOUND`；文件已删除抛 `FILE_NOT_FOUND` |
| `FileShareServiceImpl.downloadSharedFile` | `SHARE_NOT_FOUND` / `SHARE_EXPIRED` / `INVALID_TOKEN` / `FILE_NOT_FOUND` / `CANNOT_DOWNLOAD_FOLDER` 分支；成功时验证 `storageService.downloadFile(path)` 传参 |
| `FileShareServiceImpl.cancelShare` | 记录不存在抛 `SHARE_NOT_FOUND`；成功时验证 `removeFromDelayedQueue` / `removeById` / `deleteToken` 三者均被调用 |
| `FileShareServiceImpl.markExpiredShares` / `markShareExpired` | 批量/单条更新条件：`expireTime < now` 且 `isExpired=false`；异常时 `markShareExpired` 返回 false |
| `FolderShareServiceImpl.downLoadSharedFolder` | `ownerUserId=null` 抛 `NO_PERMISSION`；根非文件夹抛 `CANNOT_DOWNLOAD_FOLDER`；根不存在/已删抛 `FILE_NOT_FOUND`；`IOException` 抛 `FILE_DOWNLOAD_FAILED`；ZIP 文件名 `.zip` 后缀去重、重名加 `(n)` |
| `ShareRecordMapperServiceImpl` | `findByUserIdAndNotDel` 过滤 `isExpired=false`；`findByUserIdAndShareCode` 按用户+分享码限定；`visitedPlus1` 更新 `visitCount+1`；`expireTimeBeforeAndNotExpired` 查询已过期未标记记录 |

## 5. 测试环境说明

- **纯单测**（DTO 校验、Service 逻辑）：Mockito，不依赖中间件。
- **接口测试**（`@SpringBootTest` + `MockMvc` / TestRestTemplate）：需 Redis（Redisson 延迟队列 `share:expire:queue` + 分享 Token `share_token:{shareCode}`）；DB 可用 H2 替代 MySQL；创建/列表/取消接口需登录态（`UserContext.getCurrentUserId()`，由网关 Sa-Token 注入，直连测试时需手动构造）。
- **下载/打包接口**：需 mock `StorageService`（OSS 读取），验证响应头 `Content-Type`、`Content-Disposition` 与 ZIP 结构。
- **限流**：`@RateLimit` 注解按 USER/IP 维度生效，测试限流分支需压测或关闭限流开关单独验证。
