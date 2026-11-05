# file-download-service 测试用例

> 端口：8083 | 职责：单文件流式下载、文件夹同步/异步打包下载
> 生成日期：2026-11-04

## 1. 服务职责

文件下载中心：单文件流式下载（支持 HTTP Range 断点续传）、文件夹同步 ZIP 打包下载、文件夹异步打包任务的创建/查询/取消/结果下载。打包执行在 file-async-service，本服务负责任务管理与结果下载。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| GET | `/api/files/{fileId}/content` | 单文件流式下载（Range） | 需登录；USER+IP 2 次/s |
| GET | `/api/file/{fileId}/content` | 文件夹同步 ZIP 打包下载 | 需登录；USER+IP 2 次/s |
| POST | `/api/folder-download-tasks?folderId=` | 创建异步打包任务 | 需登录；USER+IP 1 次/s |
| GET | `/api/folder-download-tasks/{taskId}` | 查询任务状态 | 需登录；USER 10 次/s |
| DELETE | `/api/folder-download-tasks/{taskId}` | 取消任务 | 需登录；USER 3 次/s |
| GET | `/api/folder-download-tasks/{taskId}/content` | 下载打包 ZIP（Range） | 需登录；USER+IP 4 次/s |

## 3. 接口测试用例

### 3.1 单文件下载 `GET /api/files/{fileId}/content`

**正常用例**
- [ ] TC-DL-001 所有者 + 普通文件 → 返回 200，`Content-Disposition=attachment`（UTF-8 文件名）、`Content-Type`、`Content-Length`、`Accept-Ranges=bytes`
- [ ] TC-DL-002 请求头带合法 `Range: bytes=0-99` → 返回 206 + `Content-Range: bytes 0-99/{size}`，只返回前 100 字节
- [ ] TC-DL-003 文件 `fileType` 为空 → `Content-Type` 降级为 `application/octet-stream`
- [ ] TC-DL-004 空文件（size=0）→ 返回 200，`Content-Length=0`，不抛异常

**异常用例**
- [ ] TC-DL-005 文件不存在 → `FILE_NOT_FOUND`（与无权限统一，不泄露存在性）
- [ ] TC-DL-006 访问他人文件 → `FILE_NOT_FOUND`
- [ ] TC-DL-007 已删除文件 → `FILE_NOT_FOUND`
- [ ] TC-DL-008 目标是文件夹 → `CANNOT_DOWNLOAD_FOLDER`

**边界用例**
- [ ] TC-DL-009 `Range` 区间非法（start>end 或 start>=size）→ 返回 416 + `Content-Range: bytes */{size}`
- [ ] TC-DL-010 `Range` 只给 `bytes=-500`（末尾 500 字节）→ 按 `HttpRange.parse` 语义返回正确区间
- [ ] TC-DL-011 触发 USER/IP 限流（>2 次/s）→ 限流错误

### 3.2 文件夹同步打包下载 `GET /api/file/{fileId}/content`

**正常用例**
- [ ] TC-DL-012 所有者 + 文件夹 → 返回 ZIP 流（`application/zip`），ZIP 根目录为文件夹名
- [ ] TC-DL-013 空文件夹 → 返回空 ZIP（含根目录条目）

**异常用例**
- [ ] TC-DL-014 目标是普通文件（非文件夹）→ 打包失败错误
- [ ] TC-DL-015 文件夹不存在/他人文件夹 → 无权限/不存在错误

### 3.3 创建打包任务 `POST /api/folder-download-tasks`

**正常用例**
- [ ] TC-DL-016 所有者 + 合法文件夹 → 返回 `FolderDownloadTaskVO`，`status=PENDING`、`taskId` 为无横线 UUID；Redis 存任务 + 发送 MQ 消息
- [ ] TC-DL-017 路径规范化：`localFilePath` 统一以 `/` 结尾

**异常用例**
- [ ] TC-DL-018 `folderId` 不存在 → `FILE_NOT_FOUND`
- [ ] TC-DL-019 目标是普通文件 → `CANNOT_DOWNLOAD_FOLDER`（「目标不是文件夹」）
- [ ] TC-DL-020 子树文件数 >20000 → `FILE_TOO_LARGE`（「文件数量超过20000」）
- [ ] TC-DL-021 子树总大小 >200GB → `FILE_TOO_LARGE`（「文件夹总大小超过200GB」）
- [ ] TC-DL-022 MQ 投递失败 → 任务标记 `FAILED` + 返回 `SYSTEM_ERROR`

### 3.4 查询任务状态 `GET /api/folder-download-tasks/{taskId}`

**正常用例**
- [ ] TC-DL-023 本人任务 → 返回 VO（含 `status`/`progress`/`totalBytes`/`processedBytes`/`message`/`expiresAt`）
- [ ] TC-DL-024 SUCCESS 且未过期 → `downloadable=true`

**异常用例**
- [ ] TC-DL-025 `taskId` 不存在 → `FILE_NOT_FOUND`（「文件夹下载任务不存在」）
- [ ] TC-DL-026 查询他人任务 → `NO_PERMISSION`

### 3.5 取消任务 `DELETE /api/folder-download-tasks/{taskId}`

**正常用例**
- [ ] TC-DL-027 PENDING 任务取消 → `status=CANCELLED`、`message=任务已取消`
- [ ] TC-DL-028 SUCCESS 任务取消 → 删除临时压缩包 + `status=CANCELLED` + `objectKey=null`

**异常用例**
- [ ] TC-DL-029 `taskId` 不存在 → `FILE_NOT_FOUND`
- [ ] TC-DL-030 取消他人任务 → `NO_PERMISSION`

### 3.6 下载打包 ZIP `GET /api/folder-download-tasks/{taskId}/content`

**正常用例**
- [ ] TC-DL-031 SUCCESS + 未过期 + 合法 Range → 返回 206 部分内容
- [ ] TC-DL-032 SUCCESS + 无 Range → 返回 200 全量 ZIP，`Content-Disposition=attachment`，文件名 `{folderName}.zip`（去掉原 `.zip` 后缀去重）

**异常用例**
- [ ] TC-DL-033 任务非 SUCCESS 或 `objectKey=null` → `FILE_NOT_FOUND`（「压缩包尚未生成」）
- [ ] TC-DL-034 压缩包已过期（`expiresAt <= now`）→ `FILE_NOT_FOUND`（「压缩包已过期」）
- [ ] TC-DL-035 Range 非法 → 416 + `Content-Range: bytes */{size}`
- [ ] TC-DL-036 存储读取失败（IOException）→ `FILE_DOWNLOAD_FAILED`
- [ ] TC-DL-037 任务不存在/他人任务 → `FILE_NOT_FOUND` / `NO_PERMISSION`

**边界用例**
- [ ] TC-DL-038 `folderName` 为空/空白 → 下载文件名降级为 `folder.zip`

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `FileDownloadServiceImpl.prepareDownload` | 文件不存在/他人/已删除 → `FILE_NOT_FOUND`；文件夹 → `CANNOT_DOWNLOAD_FOLDER`；`recordAccess` 内部接口失败不阻断下载；文件名/类型/size 为空时的降级 |
| `FolderDownloadTaskServiceImpl.create` | 非文件夹 → `CANNOT_DOWNLOAD_FOLDER`；文件数>20000 / 总大小>200GB → `FILE_TOO_LARGE`；MQ 失败 → 标记 FAILED + `SYSTEM_ERROR`；成功时 Redis `save` + MQ 发送 |
| `FolderDownloadTaskServiceImpl.requireOwned` | 任务不存在 → `FILE_NOT_FOUND`；他人任务 → `NO_PERMISSION` |
| `FolderDownloadTaskServiceImpl.getDownloadableTask` | 非 SUCCESS / objectKey 为空 / 过期 → `FILE_NOT_FOUND` |
| `FolderDownloadTaskServiceImpl.cancel` | PENDING 直接取消；SUCCESS 删除临时压缩包，`deleteFile` 失败不阻断 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock mapper / storage / MQ / Redis，不依赖中间件。
- **接口测试**：需登录态 + Redis（`FolderDownloadRedis` 存任务）；Storage 可用 mock（OSS 读取/流）。
- **Range 断点续传**：重点验证 200/206/416 三种状态码与 `Content-Range` 头。
- **跨服务**：`create` 投递 MQ 到 file-async-service 执行打包，端到端需 RabbitMQ + file-async-service。
