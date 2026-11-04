P0 先让环境真正跑起来（现在只有编译级验证）

1. 重新生成 Nacos 配置包：从 D:\下载\nacos_config_export_20261020152156 原始导出重建 nacos-config/DEFAULT_GROUP/（18 个 dataId）+ README + 可直接导入的 zip，并实际导入 Nacos 确认 18 条
2. 基础设施编排 docker-compose.yml：MySQL 8 / Redis 7 / RabbitMQ 3.13（management）/ Elasticsearch 8.11 / pgvector（pg16）/ Nacos 2.x；数据卷 + 健康检查
3. 12 个微服务镜像：每个服务 Dockerfile（或用统一模板 + 参数），compose 里按依赖关系启动（Nacos → 基础设施 → file-async-service → 业务服务 → 网关）
4. 初始化脚本 db/：MySQL 建表 SQL（user_center/file_info/file_object/recent_access/share_record/operation_log/ai_* 等）、PG file_segment、ES file_index mapping——现在没有版本化迁移，靠手工建表
5. 重写 .env.example：按 Nacos 占位符整理 MYSQL_* / REDIS_* / RABBITMQ_* / MAIL_* / OSS_* / AI_* / NACOS_*，并标注启动顺序（RabbitMQ → async 建队列 → 其它）
6. 全链路冒烟（起环境后逐条过）：登录 → 普通/秒传/分片/直传上传 → 文件管理/回收站 → 单文件下载+Range → 文件夹同步 ZIP + 异步打包 → 分享全流程 → 搜索（Mysql/ES 两种）→ 预览 → WebSocket 用量消息 → 6 个定时任务 → AI 对话/Agent
7. 提交当前 RabbitMqTopologyConfig 修复
   P1 功能与正确性收尾
8. Feign 返回类型适配：/internal/space/* 返回 Result<T>，Feign 声明裸 Boolean/SpaceUsage 运行时会反序列化失败；加 Result decoder 或内部接口改直返
9. /internal/** 服务间鉴权：目前只有 /internal/space、/internal/recent 两个端点，无任何校验
10. 异步事件补齐：ai-service 的 Agent 写 t_file_info 后未发 ES/PG/操作日志；share-service 分享创建/取消未发操作日志
11. 远程调用规范化：Feign 加超时/重试/熔断（Resilience4j）+ trace 透传（Micrometer tracing），服务名走 lb://
12. MQ 可靠性（你说先不加幂等，列为可选）：operation_log.msg_id 唯一索引 + 先落库后去重、DLQ 重投处理
13. 清理死代码：DirectUploadServiceImpl.cleanupExpired()、重复工具类、IDE 残留引用等
    P2 工程质量
14. 单测/集成测试：目前 12 个服务基本是 0 测试，先用 Testcontainers 把核心链路（上传/下载/分享/搜索）测起来
15. 可观测性：Prometheus + Grafana、OTel 链路、日志聚合、告警；网关访问日志
16. CI/CD：mvn verify + 镜像构建 + 部署流水线
17. 安全：git 历史里的 RabbitMQ 明文凭据轮换、Nacos 敏感配置加密、OSS STS 最小权限、网关限流/CORS、依赖漏洞扫描
18. 文档：README 更新、部署手册、API 文档、架构图



