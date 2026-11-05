# file-preview-service 测试用例

> 端口：8087 | 职责：文件在线预览（OSS 预签名 URL）
> 生成日期：2026-11-04

## 1. 服务职责

根据文件 ID 校验归属后，生成 5 分钟有效的 OSS 预签名 URL，并返回预览分类（image/video/audio/pdf/text）供前端选择渲染组件。非所有者统一返回无权限，不泄露文件存在性。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| GET | `/api/files/preview/{fileId}` | 获取文件预览信息 | 需登录；USER+IP 2 次/s |

## 3. 接口测试用例

### 3.1 获取预览信息 `GET /api/files/preview/{fileId}`

**正常用例**
- [ ] TC-PV-001 所有者 + 可预览后缀（如 `.png`）→ 返回 `PreviewVO`，含 `url`（非空预签名 URL）、`category`（image）、`mimeType`、`filename`、`fileSize`
- [ ] TC-PV-002 预签名 URL 有效期为 300 秒（5 分钟，`URL_EXPIRY_SECONDS`）
- [ ] TC-PV-003 配置了 `oss.cdn-endpoint` 时 → 走 CNAME 模式签名（`setSupportCname(true)`），URL host 为 CDN 域名
- [ ] TC-PV-004 文件名补全后缀：`fullName = filename + "." + suffix`
- [ ] TC-PV-005 预签名 URL 携带 `response-content-disposition=inline`（浏览器内联预览而非下载）

**异常用例**
- [ ] TC-PV-006 文件不存在 → 返回 `NO_PERMISSION`（与无权限统一，不泄露存在性）
- [ ] TC-PV-007 访问他人文件（userId 不匹配）→ 返回 `NO_PERMISSION`
- [ ] TC-PV-008 文件已删除（`isDeleted=true`）→ 返回 `NO_PERMISSION`
- [ ] TC-PV-009 后缀不支持预览（如 `.exe`/`.zip`）→ 返回 `FILE_NOT_SUPPORT_PREVIEW`
- [ ] TC-PV-010 OSS 未启用（`oss.enabled=false`）→ 返回 `OSS_DISABLED`

**边界用例**
- [ ] TC-PV-011 `fileId` 为 null → 接口异常（`@PathVariable` 无校验，需确认返回 400 还是 500）
- [ ] TC-PV-012 无后缀文件 → `fullName` 不加 `.`（`suffix` 为 null/空时直接返回原 filename）
- [ ] TC-PV-013 触发 USER/IP 限流（>2 次/s）→ 返回限流错误

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `PreviewServiceImpl.preview` | 归属校验（id+userId+未删除）为 null → `NO_PERMISSION`；后缀不支持 → `FILE_NOT_SUPPORT_PREVIEW`；成功路径验证 `generatePresignedUrl(path, 300, mimeType)` 传参正确 |
| `OssStorageServiceImpl.generatePresignedUrl` | `enabled=false` → `OSS_DISABLED`；CDN 配置时 `setSupportCname(true)`；`response-content-disposition=inline` 参数存在；`finally` 中 `ossClient.shutdown()` 被调用 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock `FileInfoMapper` / `OssStorageService`，不依赖中间件。
- **接口测试**：需登录态（`UserContext.getCurrentUserId()`，由网关 Sa-Token 注入）；`FileInfoMapper` 可用 H2 替代 MySQL。
- **OSS 相关**：预签名 URL 生成逻辑可 mock `OssStorageService`；真实签名需阿里云 OSS 或 `OSS_ENABLED=false` 直接验证 `OSS_DISABLED` 分支。
