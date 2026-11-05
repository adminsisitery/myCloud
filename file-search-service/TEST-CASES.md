# file-search-service 测试用例

> 端口：8089 | 职责：文件搜索（Mysql LIKE 默认，可切 ES）
> 生成日期：2026-11-04

## 1. 服务职责

按关键词搜索当前用户的文件。搜索策略由 `file.search` 配置决定：`Mysql`（默认，数据库 LIKE）或 `ES`（Elasticsearch 文件名/内容检索后回查数据库校验归属）。

## 2. 接口清单

| 方法 | 路径 | 说明 | 鉴权/限流 |
|---|---|---|---|
| GET | `/api/files/search?keyword=xxx` | 按关键词搜索文件 | 需登录；USER 5 次/s |

## 3. 接口测试用例

### 3.1 搜索 `GET /api/files/search`

**正常用例**
- [ ] TC-SC-001 `file.search=Mysql` + 有匹配关键词 → 返回 `FileListVO` 列表（按文件名 LIKE 匹配）
- [ ] TC-SC-002 `file.search=ES` + 有匹配关键词 → 文件名/内容两路检索合并去重，回查 DB 校验归属后返回
- [ ] TC-SC-003 无匹配关键词 → 返回空列表（`[]`）
- [ ] TC-SC-004 搜索只返回当前用户自己的文件（`userId` 隔离）

**异常用例**
- [ ] TC-SC-005 未登录 → 返回未登录错误
- [ ] TC-SC-006 `file.search=ES` 但 ES 不可达 → 抛出异常（连接失败）
- [ ] TC-SC-007 触发 USER 限流（>5 次/s）→ 返回限流错误

**边界用例**
- [ ] TC-SC-008 `keyword` 为空串 → 走 LIKE `%%` 匹配全部（`MySQLFileSearchHandler` 无空值拦截，需确认产品预期是否应拦截空关键词）
- [ ] TC-SC-009 `keyword` 含 SQL LIKE 通配符（`%`、`_`）→ 需确认是否转义（否则 `%` 会匹配任意字符，属潜在缺陷）
- [ ] TC-SC-010 超长关键词 → 不抛异常，返回空或正常

**策略切换用例**
- [ ] TC-SC-011 `file.search=Mysql`（或未配置）→ 仅 `MySQLFileSearchHandler` bean 生效
- [ ] TC-SC-012 `file.search=ES` → 仅 `ESFileSearchHandler` + `FileSearchServiceImpl` bean 生效（`@ConditionalOnProperty` 互斥）

## 4. 关键 Service 单元测试点（Mockito）

| 目标 | 测试点 |
|---|---|
| `MySQLFileSearchHandler.searchFiles` | 调用 `findBykeyWord(keyword, userId)`，结果经 `FileMapper.toFileListVO` 映射 |
| `ESFileSearchHandler.searchFiles` | 委托 `FileSearchService.searchFile` 并映射 VO |
| `FileSearchServiceImpl.searchFile` | 文件名+内容两路检索结果用 `LinkedHashSet` 合并去重；`fileIds` 为空直接返回 `List.of()`；回查 `findInIdAndUserId` 校验归属；非文件夹补后缀 |

## 5. 测试环境说明

- **纯单测**：Mockito，mock handler / mapper，不依赖中间件。
- **Mysql 策略接口测试**：需登录态 + MySQL（H2 可替代，但 LIKE 语法需与 MySQL 一致）。
- **ES 策略接口测试**：需真实 ES（`file.search=ES` 时 `ElasticsearchRepositoriesAutoConfiguration` 已排除，但 `FileSearchRepository` 由 `ElasticsearchRepositoryConfig` 按条件启用），需预置 `file_index` 索引数据。
