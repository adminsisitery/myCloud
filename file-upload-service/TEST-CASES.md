# file-upload-service 测试用例

> 端口：8082 | 职责：普通上传（带进度）、分片上传、STS 直传、秒传
> 生成日期：2026-11-04

## 1. 服务职责

文件上传中心，四种上传链路：普通上传（异步+进度轮询）、大文件分片上传（>500MB，OSS MultipartUpload）、STS 直传（客户端直传 OSS）、秒传（SHA-256 判重，免传字节）。空间配额通过 user-service 内部接口预占/释放。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| POST | `/api/files/progress` | 普通上传（异步，返回任务 ID） | 需登录；USER+IP 2 次/s |
| POST | `/api/files/check` | 秒传判定（无副作用） | 需登录；USER+IP 2 次/s |
| POST | `/api/files/fast-upload` | 秒传执行（落库） | 需登录；USER+IP 2 次/s |
| POST | `/api/chunk/init` | 初始化分片上传 | 需登录；USER+IP 2 次/s |
| POST | `/api/chunk/upload` | 上传单个分片 | 需登录；USER 20 次/s |
| POST | `/api/chunk/complete` | 合并分片 | 需登录；USER 5 次/s |
| GET | `/api/chunk/progress/{uploadId}` | 查询分片进度 | 需登录；USER 10 次/s |
| DELETE | `/api/chunk/cancel/{uploadId}` | 取消分片上传 | 需登录；USER 5 次/s |
| POST | `/api/direct-upload/init` | STS 直传初始化 | 需登录；USER+IP 3 次/s |
| POST | `/api/direct-upload/{taskId}/complete` | 直传完成确认 | 需登录；USER 5 次/s |
| DELETE | `/api/direct-upload/{taskId}` | 取消直传 | 需登录；USER 5 次/s |
| GET | `/api/upload-progress/{taskId}` | 查询上传任务进度 | 需登录；USER+IP 10 次/s |
| GET | `/api/upload-progress/{taskId}/cancel` | 取消上传任务 | 需登录 |

## 3. 接口测试用例

### 3.1 普通上传 `POST /api/files/progress`

**正常用例**
- [ ] TC-UP-001 合法文件 + `parentId` → 返回上传任务 ID（`Result<String>`），任务异步上传
- [ ] TC-UP-002 不传 `parentId` → 默认 `0`（根目录）

**异常用例**
- [ ] TC-UP-003 空文件（`MultipartFile` 为空）→ 返回参数错误
- [ ] TC-UP-004 `parentId` 不存在 → 目标目录不存在错误

### 3.2 秒传判定 `POST /api/files/check`

**正常用例**
- [ ] TC-UP-005 已存在的 `sha256Hash` + `fileSize` → 返回 `exists=true`（可秒传）
- [ ] TC-UP-006 不存在的 hash → 返回 `exists=false`

**异常/参数用例**
- [ ] TC-UP-007 `sha256Hash` 非 64 位十六进制 → `INVALID_PARAM`（「sha256Hash 必须是 64 位十六进制字符串」）
- [ ] TC-UP-008 `sha256Hash`/`fileSize` 为 null → 判定为不存在（不报错）

### 3.3 秒传执行 `POST /api/files/fast-upload`

**正常用例**
- [ ] TC-UP-009 合法请求 + 物理对象存在 → 返回 `data.fileId`，落库文件记录
- [ ] TC-UP-010 幂等：目标目录已有同内容同名记录 → 返回原记录 `fileId`，不重复插入

**异常/参数用例**
- [ ] TC-UP-011 `sha256Hash` 格式非法 → `INVALID_PARAM`
- [ ] TC-UP-012 `fileName` 为空 → `INVALID_PARAM`（「fileName 不能为空」）
- [ ] TC-UP-013 `fileSize <= 0` → `INVALID_PARAM`（「fileSize 必须大于 0」）
- [ ] TC-UP-014 物理对象不存在（hash+size 不匹配）→ `FAST_UPLOAD_UNAVAILABLE`
- [ ] TC-UP-015 `parentId` 目标目录不存在 → `FILE_NOT_FOUND`（「目标目录不存在」）

### 3.4 初始化分片 `POST /api/chunk/init`

**正常用例**
- [ ] TC-UP-016 大文件（>500MB）+ 合法参数 → 返回自定义 `uploadId` + OSS `uploadId`，预占空间
- [ ] TC-UP-017 空间充足时 → 预占成功

**异常/边界用例**
- [ ] TC-UP-018 参数校验失败（`totalParts`/`fileSize`/文件名非法）→ 返回校验错误
- [ ] TC-UP-019 用户可用空间不足 → 返回「用户可用空间不足」

### 3.5 上传分片 `POST /api/chunk/upload`

**正常用例**
- [ ] TC-UP-020 合法 `uploadId` + `partNumber` + 分片数据 → 上传成功，返回分片信息
- [ ] TC-UP-021 断点续传：已上传的分片被记录，`progress` 能查到

**异常/边界用例**
- [ ] TC-UP-022 `uploadId` 不存在或已过期 → 「uploadId不存在或已过期」
- [ ] TC-UP-023 上传他人任务 → 「无权访问此上传任务」
- [ ] TC-UP-024 `partNumber` 超出范围 → 「分片序号超出范围」
- [ ] TC-UP-025 分片内容为空 → 「分片内容不能为空」
- [ ] TC-UP-026 同分片并发上传 → 「分片正在上传，请稍后重试」
- [ ] TC-UP-027 分片大小与初始化参数不一致 → 「分片大小与初始化参数不一致」

### 3.6 合并分片 `POST /api/chunk/complete`

**正常用例**
- [ ] TC-UP-028 全部分片已上传 → 合并成功，返回文件信息，释放预占空间

**异常/边界用例**
- [ ] TC-UP-029 `uploadId` 不存在/过期 → 「uploadId不存在或已过期」
- [ ] TC-UP-030 缺失分片 → 「缺失分片号: ...」（列出缺失分片）
- [ ] TC-UP-031 任务正在合并 → 「任务正在合并，请稍后查询」
- [ ] TC-UP-032 合并被并发中断 → 「合并请求被中断」

### 3.7 分片进度 `GET /api/chunk/progress/{uploadId}`

- [ ] TC-UP-033 合法 `uploadId` → 返回已完成/未完成分片列表
- [ ] TC-UP-034 `uploadId` 不存在 → 「uploadId不存在或已过期」
- [ ] TC-UP-035 他人任务 → 「无权访问此上传任务」

### 3.8 取消分片 `DELETE /api/chunk/cancel/{uploadId}`

- [ ] TC-UP-036 本人任务 → 取消成功，释放预占空间，OSS MultipartUpload 中止
- [ ] TC-UP-037 任务正在处理 → 「任务正在处理，请稍后重试」
- [ ] TC-UP-038 `uploadId` 不存在/他人 → 对应错误

### 3.9 STS 直传初始化 `POST /api/direct-upload/init`

**正常用例**
- [ ] TC-UP-039 合法请求 → 返回 STS 临时凭证（AccessKeyId/Secret/Token）+ 直传任务 ID
- [ ] TC-UP-040 凭证有效期在 900~3600 秒之间

**异常/边界用例**
- [ ] TC-UP-041 未配置 `oss.sts.role-arn` → `OSS_DISABLED`（「STS直传不可用」）
- [ ] TC-UP-042 目标文件夹不存在/无权访问 → `FILE_NOT_FOUND`
- [ ] TC-UP-043 用户可用空间不足 → `FILE_TOO_LARGE`
- [ ] TC-UP-044 STS 凭证获取失败 → `OSS_UPLOAD_FAILED`

### 3.10 直传完成 `POST /api/direct-upload/{taskId}/complete`

**正常用例**
- [ ] TC-UP-045 本人任务 + OSS 对象已就绪 → 校验通过，保存文件元数据，返回文件信息

**异常用例**
- [ ] TC-UP-046 任务不存在或已完成 → `FILE_NOT_FOUND`（「直传任务不存在或已完成」）
- [ ] TC-UP-047 他人任务 → `NO_PERMISSION`
- [ ] TC-UP-048 任务正在确认 → `FILE_UPLOAD_FAILED`（「直传任务正在确认」）
- [ ] TC-UP-049 任务已过期 → `FILE_UPLOAD_FAILED`（「直传任务已过期」）
- [ ] TC-UP-050 OSS 对象大小与初始化参数不一致 → `FILE_UPLOAD_FAILED`
- [ ] TC-UP-051 保存文件元数据失败 → `FILE_UPLOAD_FAILED`

### 3.11 取消直传 `DELETE /api/direct-upload/{taskId}`

- [ ] TC-UP-052 本人任务 → 取消成功
- [ ] TC-UP-053 任务正在处理 → `FILE_UPLOAD_FAILED`（「取消直传被中断」）

### 3.12 上传进度查询/取消 `/api/upload-progress/{taskId}`

- [ ] TC-UP-054 合法 `taskId` → 返回进度 VO（status/进度）
- [ ] TC-UP-055 任务不存在 → 「上传任务不存在」
- [ ] TC-UP-056 UPLOADING/PENDING 状态取消 → 「取消成功」
- [ ] TC-UP-057 非 UPLOADING/PENDING 状态取消 → 「取消失败」

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `FileFastUploadServiceImpl.checkFilesHash` | hash 格式校验；命中/未命中分支 |
| `FileFastUploadServiceImpl.fastUpload` | 参数校验（hash/fileName/fileSize）；物理对象不存在 → `FAST_UPLOAD_UNAVAILABLE`；幂等（同内容同名返回原记录） |
| `ChunkUploadServiceImpl.initUpload` | 参数校验、空间预占、OSS MultipartUpload 创建 |
| `ChunkUploadServiceImpl.uploadPart` | uploadId 归属/过期、分片序号范围、分片空、并发锁、大小一致性 |
| `ChunkUploadServiceImpl.completeChunk` | 缺失分片检测、并发合并锁、合并后释放预占空间 |
| `ChunkUploadServiceImpl.cancelUpload` | 释放预占空间 + 中止 OSS MultipartUpload |
| `DirectUploadServiceImpl.init` | `role-arn` 缺失 → `OSS_DISABLED`；STS 凭证有效期校验；空间预占 |
| `DirectUploadServiceImpl.complete` | 任务锁（正在确认/处理）、过期校验、OSS 对象大小一致性、元数据落库 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock mapper / OSS / STS / 空间内部客户端，不依赖中间件。
- **接口测试**：需登录态 + Redis（上传任务/进度/直传任务缓存）+ DB（H2 可替代）。
- **OSS/STS**：`OSS_ENABLED=false` 或未配 `role-arn` 时直接验证 `OSS_DISABLED` 分支；真实分片/直传需阿里云 OSS。
- **跨服务**：空间预占调 user-service `/internal/space/reserve`，需 mock 或联调 user-service。
