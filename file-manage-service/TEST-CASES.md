# file-manage-service 测试用例

> 端口：8084 | 职责：文件/文件夹管理：列表、新建、重命名、移动、复制、收藏、最近访问、回收站
> 生成日期：2026-11-04

## 1. 服务职责

文件与文件夹管理：目录列表、最近访问、重命名、移动、复制、收藏、回收站（删除/恢复/彻底删除/清空）。删除与彻底删除会触发空间释放/物理删除，最近访问记录供下载服务经内部接口回调写入。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| GET | `/api/files?parentId=` | 目录文件列表 | 需登录；USER+IP 10 次/s |
| GET | `/api/files/recent` | 最近访问列表 | 需登录；10 次/s |
| GET | `/api/files/starred` | 收藏列表 | 需登录；10 次/s |
| PATCH | `/api/files/{fileId}/name` | 重命名 | 需登录；5 次/s |
| PATCH | `/api/files/{fileId}/move` | 移动 | 需登录；5 次/s |
| POST | `/api/files/copy/{fileId}` | 复制 | 需登录；2 次/s |
| POST | `/api/files/{fileId}/star` | 收藏 | 需登录；5 次/s |
| DELETE | `/api/files/{fileId}/star` | 取消收藏 | 需登录；5 次/s |
| POST | `/api/file/folder` | 新建文件夹 | 需登录；1 次/s |
| DELETE | `/api/files/{fileId}` | 删除单个（进回收站） | 需登录；5 次/s |
| DELETE | `/api/files/batch?ids=` | 批量删除（进回收站） | 需登录；5 次/s |
| DELETE | `/api/files/tureDelete?fileIds=` | 彻底删除（物理） | 需登录；5 次/s |
| GET | `/api/files/trash` | 回收站列表 | 需登录；5 次/s |
| PUT | `/api/files/trash/restore/{fileId}` | 恢复文件 | 需登录；5 次/s |
| DELETE | `/api/files/trash/clean` | 清空回收站 | 需登录；5 次/s |
| POST | `/internal/recent/record` | 记录最近访问（内部） | 内网 |

## 3. 接口测试用例

### 3.1 目录列表 `GET /api/files`

**正常用例**
- [ ] TC-MG-001 合法 `parentId` → 返回该目录下文件/文件夹列表（`FileListVO`）
- [ ] TC-MG-002 不传 `parentId` → 默认根目录 `0`

**异常用例**
- [ ] TC-MG-003 `parentId` 不存在/他人目录 → 空列表或错误（按实现确认）

### 3.2 重命名 `PATCH /api/files/{fileId}/name`

**正常用例**
- [ ] TC-MG-004 本人文件 + 合法新名 → 重命名成功，DB `filename` 更新
- [ ] TC-MG-005 重命名发送 MQ 操作日志（RENAME）

**异常/边界用例**
- [ ] TC-MG-006 新文件名为空 → 「新文件名不能为空」
- [ ] TC-MG-007 文件不存在 → 「文件不存在」
- [ ] TC-MG-008 他人文件 → 「无权操作该文件」
- [ ] TC-MG-009 文件名长度 >50 或含非法字符 → 「文件名不合法」
- [ ] TC-MG-010 同级已存在同名文件 → 「目标目录已存在同名文件」

### 3.3 移动 `PATCH /api/files/{fileId}/move`

**正常用例**
- [ ] TC-MG-011 本人文件 + 合法目标目录 → 移动成功

**异常/边界用例**
- [ ] TC-MG-012 文件不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-013 目标父目录不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-014 目标是普通文件（非文件夹）→ `FOLDER_NOT_EMPTY`（「目标不是文件夹」）
- [ ] TC-MG-015 移动到自己的子目录 → `CANNOT_MOVE_INTO_OWN_SUBTREE`
- [ ] TC-MG-016 移动到同一目录（无变化）→ 幂等或错误（按实现确认）

### 3.4 复制 `POST /api/files/copy/{fileId}`

- [ ] TC-MG-017 本人文件 → 复制成功，生成副本
- [ ] TC-MG-018 文件不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-019 复制文件夹 → 递归复制子树

### 3.5 收藏/取消收藏 `POST|DELETE /api/files/{fileId}/star`

- [ ] TC-MG-020 本人文件收藏 → 成功，出现在收藏列表
- [ ] TC-MG-021 取消收藏 → 成功，从收藏列表移除
- [ ] TC-MG-022 文件不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-023 收藏列表 → 返回当前用户收藏项

### 3.6 新建文件夹 `POST /api/file/folder`

- [ ] TC-MG-024 合法 `parentId` + 文件夹名 → 创建成功
- [ ] TC-MG-025 文件夹名为空/非法 → 校验错误
- [ ] TC-MG-026 同级已存在同名文件夹 → 重名冲突错误

### 3.7 删除到回收站 `DELETE /api/files/{fileId}` / `/api/files/batch`

**正常用例**
- [ ] TC-MG-027 本人文件删除 → 进入回收站（`isDeleted=true` + 删除时间），不出现在列表
- [ ] TC-MG-028 批量删除（逗号分隔 ids）→ 全部进回收站

**异常用例**
- [ ] TC-MG-029 文件不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-030 他人文件 → `NO_PERMISSION`
- [ ] TC-MG-031 删除文件夹 → 子树一并进回收站

### 3.8 彻底删除 `DELETE /api/files/tureDelete`

- [ ] TC-MG-032 回收站内文件彻底删除 → 物理删除（DB 记录 + OSS 对象），释放空间
- [ ] TC-MG-033 文件不在回收站 → 错误（不允许直接物理删除正常文件，按实现确认）
- [ ] TC-MG-034 他人文件 → `NO_PERMISSION`

### 3.9 回收站列表 `GET /api/files/trash`

- [ ] TC-MG-035 返回当前用户回收站列表（`TrashListVO`）

### 3.10 恢复 `PUT /api/files/trash/restore/{fileId}`

**正常用例**
- [ ] TC-MG-036 回收站内文件恢复 → 回到原目录，`isDeleted=false`，空间占用恢复

**异常用例**
- [ ] TC-MG-037 文件不存在 → `FILE_NOT_FOUND`
- [ ] TC-MG-038 文件不在回收站 → `FILE_NOT_IN_TRASH`
- [ ] TC-MG-039 他人文件 → `NO_PERMISSION`

### 3.11 清空回收站 `DELETE /api/files/trash/clean`

- [ ] TC-MG-040 清空当前用户回收站 → 全部物理删除 + 释放空间

### 3.12 内部接口 `POST /internal/recent/record`

- [ ] TC-MG-041 合法 userId + fileId → 写入 `t_recent_access`，返回「记录成功」

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `FileManageServiceImpl.renameFile` | 空名/文件不存在/无权限/超长/非法字符/重名冲突 六条失败路径 + 成功路径（含 MQ 操作日志） |
| `FileManageServiceImpl.moveFile` | 文件不存在/目标不存在/目标非文件夹/移动到自身子树 四类异常 |
| `FileManageServiceImpl.copyFile` | 文件/文件夹递归复制 |
| `RecycleBinServiceImpl.deleteSingleFile`/`batchDeleteFile` | 文件不存在/无权限；进回收站状态更新；空间释放调用 |
| `RecycleBinServiceImpl.tureDeleteFile` | 物理删除（DB + OSS）；不在回收站拦截 |
| `RecycleBinServiceImpl.restoreFile` | 文件不存在/不在回收站/无权限；恢复状态 + 空间恢复（`addUsedSpace`） |
| `RecycleBinServiceImpl.cleanTrash` | 批量物理删除 + 空间释放 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock mapper / MQ / Storage，不依赖中间件。
- **接口测试**：需登录态 + DB（H2 可替代）+ MQ（操作日志可 mock）；删除/恢复涉及空间内部接口（user-service `/internal/space/*`），需 mock 或联调。
- **回收站 30 天清理**：定时清理在 file-task-service，端到端需联调。
