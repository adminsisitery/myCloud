# CLAUDE.md

本文件为 Claude Code 在本仓库工作时提供指引。

**维护约定**：本文件需随项目实时更新——新增/删除模块、变更端口、引入或移除技术栈/中间件、调整构建命令或约定时，同步更新本文件，避免内容过时。

## 项目概述

MyCloud 是一个基于 Java 21 + Spring Boot 3.2.3 的微服务化个人云盘后端。原单体（`src/`，8080）已退场，业务能力全部拆分到各微服务模块，根 `pom.xml` 只做聚合（`com.cloudrive:cloud-drive-backend`）。

## 技术栈

- Java 21、Spring Boot 3.2.3、Spring Cloud Gateway、Maven（聚合工程）
- 存储/中间件：MySQL、Redis、RabbitMQ、Elasticsearch、PostgreSQL + pgvector、Nacos、阿里云 OSS
- AI：DeepSeek（对话）、阿里云 DashScope（embedding，`text-embedding-v4`）、Langfuse 观测、Tavily 联网搜索
- 鉴权：Sa-Token（网关校验）、GitHub OAuth

## 模块结构

| 模块 | 端口 | 职责 |
|---|---|---|
| `pan-common` | — | 公共库：Result / ErrorCode / 工具类、MQ 消息体、文件夹任务 DTO |
| `pan-gateway` | 8090 | Spring Cloud Gateway 网关（HTTP + WebSocket 路由、Sa-Token 登录校验） |
| `user-service` | 8081 | 用户/登录/注册/GitHub OAuth/空间配额（`/internal/space/**`） |
| `file-upload-service` | 8082 | 分片上传/STS 直传/普通上传/秒传/进度/OSS 存储 |
| `file-download-service` | 8083 | 单文件流式下载、文件夹同步 ZIP、异步打包任务 HTTP 接口 |
| `file-manage-service` | 8084 | 文件/文件夹管理：列表、新建文件夹、重命名、移动、复制、收藏、最近访问、回收站 |
| `file-share-service` | 8085 | 分享创建/列表/取消、密码与 Token 校验、分享内容下载 |
| `file-async-service` | 8086 | MQ 消费端：ES/PG 索引同步、操作日志落库、用量通知（WebSocket `/ws/message` + 邮件）、文件夹打包执行 |
| `file-preview-service` | 8087 | 文件在线预览（OSS 预签名 URL） |
| `ai-service` | 8088 | AI 对话 / Agent / RAG / 记忆 / 用量统计 |
| `file-search-service` | 8089 | 文件搜索（`file.search=Mysql` 默认数据库 LIKE，可切 ES） |
| `file-task-service` | 8091 | 集中定时任务：上传/分片/直传超时清理、分享过期、文件夹打包清理、回收站 30 天清理 |

## 构建

```bash
export JAVA_HOME=/path/to/jdk21
mvn -DskipTests clean package        # 全量构建
mvn -DskipTests -pl <module> -am package   # 单模块构建
```

产物为各模块 `target/*-service-1.0-SNAPSHOT.jar`。

注意：Maven Wrapper 缺少 `.mvn/wrapper/maven-wrapper.properties`，请使用本机 Maven 3.9+。

**构建校验**：改动 Java 代码后，运行编译命令（如 `mvn -q compile`）并报告结果，编译通过后再视为任务完成。

## 运行依赖

MySQL、Redis、RabbitMQ、Elasticsearch、PostgreSQL + pgvector、Nacos、阿里云 OSS；AI 域另需 DeepSeek / OpenAI 兼容 Key。各服务通过环境变量注入连接信息（详见各模块 `src/main/resources/application.yml`，如 `MYSQL_HOST` / `REDIS_HOST` / `RABBITMQ_HOST` / `NACOS_SERVER_ADDR` / `OSS_*` / `AI_*`）。`.env.example` 为部署侧环境变量样例。

## 网关路由

`pan-gateway` 按精确路径把请求转发到对应服务（上传/下载/预览/搜索的精确路由优先于 `/api/files/**` 管理路由），未匹配的请求直接返回 404。`/ws/message` 走 `file-async-service`，AI 的 SSE 流式接口走 `ai-service`。

## 开发约定 / 已知限制

- 运行期服务间调用目前使用 RestClient + 环境变量地址（Nacos 只用于网关侧服务发现）。RPC 规范化（`lb://` + 超时/重试/熔断/trace）与 `/internal/**` 服务间鉴权在后续迭代补齐。
- 部署编排（各服务 Dockerfile / compose / 基础设施编排 / 健康检查）尚未落地，当前仓库不再包含旧的单体 `Dockerfile` 与 `compose.yml`。

## 项目映射（简历 / 描述项目时用）

- **mycloud（本仓库）**：Java 21 + Spring Boot 3.2.3 微服务个人云盘后端（详见上文）。用户在本项目的具体职责/技术栈：待补充。
- **FLYDOC**：职责/技术栈：待补充。
- **xmlAnd**：职责/技术栈：待补充。

**简历 / 项目描述规则**：不确定时先问，不要假设。

**问题约定**：有什么你没办法做到的，及时和我说，不要绕过去


