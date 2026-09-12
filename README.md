# MyCloud

MyCloud 是一个基于 Java 21 与 Spring Boot 3.2.3 的个人云盘后端。项目同时包含文件管理、OSS 对象存储、分片与 STS 直传、异步文件夹打包、分享、搜索、WebSocket 通知，以及基于 LangChain4j 的 RAG/Agent 能力。

项目的统一说明入口：

- [项目说明书](docs/project-handbook.md)：架构、模块、数据模型、核心流程、接口分组、运行依赖、风险与文档更新规则。
- [API 接口文档](docs/cloud-drive-api.md)：较详细的请求与响应示例；使用前应与 Controller 当前实现核对。
- [上传与断点下载指南](docs/upload-backend-guide.md)：STS 直传、后端分片、Range 下载和文件夹异步下载。
- [路线图](docs/roadmap.md) 与 [剩余功能](docs/remaining-features.md)：规划材料，不代表功能已经实现。

## 当前验证基线

- 文档快照：2026-08-08，基于当前工作区（包括尚未提交的改动）。
- CodeGraph：228 个文件、5,265 个节点、10,512 条关系，已使用当前引擎重新索引。
- 测试：`mvn test`，101 个测试全部通过。
- Maven Wrapper 当前缺少 `.mvn/wrapper/maven-wrapper.properties`，请暂时使用本机 Maven。

## 快速开始

1. 准备 MySQL、Redis、RabbitMQ、Elasticsearch、PostgreSQL + pgvector 和阿里云 OSS。
2. 将 `application.yml` 中的敏感配置迁移为环境变量或外部配置，禁止继续提交真实密钥。
3. 执行 `docs/db/` 中尚未应用的迁移脚本。
4. 使用 Java 21 执行 `mvn test`，再执行 `mvn spring-boot:run`。
5. 默认服务端口及 Swagger 地址以 `application.yml` 为准。

> 安全提示：当前代码和配置中存在硬编码凭据，部署或共享仓库前必须轮换并清理。详细项见项目说明书的“已知风险与技术债”。
