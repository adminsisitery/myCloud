-- ============================================================
-- V1：myCloud MySQL 初始 schema
-- ============================================================
-- 执行者：user-service（MySQL 数据归属方，其余服务只读）
-- 约定：
--   * 全部 InnoDB + utf8mb4，时间列统一 DATETIME（对应 LocalDateTime）
--   * 全部 CREATE TABLE IF NOT EXISTS —— 已有手工建过表的环境也能安全跑过
--   * 列名以 MyBatis-Plus 实体为准：默认驼峰转下划线，
--     实体上显式 @TableField 的列（isFolder/isDeleted/isStarred/local_file_path）按注解写
-- ============================================================

-- ------------------------------------------------------------
-- 用户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(100) DEFAULT NULL COMMENT 'BCrypt 密码哈希；第三方登录账号可为空',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `avatar`      VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    `github_id`   VARCHAR(64)  DEFAULT NULL COMMENT 'GitHub 用户 ID（OAuth 登录）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用',
    `create_time` DATETIME     DEFAULT NULL COMMENT '注册时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_github_id` (`github_id`),
    KEY `idx_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户账号';

-- ------------------------------------------------------------
-- 用户空间配额（数据归属 user-service，其他服务只经 /internal/space 读写）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_center`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '用户 ID',
    `username`    VARCHAR(64) DEFAULT NULL COMMENT '用户名（冗余，便于排查）',
    `used_space`  BIGINT      NOT NULL DEFAULT 0 COMMENT '已用空间（字节）',
    `total_space` BIGINT      NOT NULL DEFAULT 0 COMMENT '总配额（字节）',
    `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户空间配额';

-- ------------------------------------------------------------
-- 文件元信息（file-manage 写，其余服务只读）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_file_info`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `filename`          VARCHAR(255) NOT NULL COMMENT '文件（夹）名称',
    `original_filename` VARCHAR(255) DEFAULT NULL COMMENT '上传时的原始文件名',
    `path`              VARCHAR(512) DEFAULT NULL COMMENT '云盘内路径 / 对象存储 key',
    `file_size`         BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节），文件夹为 0',
    `file_type`         VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    `sha256_hash`       CHAR(64)     DEFAULT NULL COMMENT '内容 SHA256，用于秒传与完整性校验',
    `file_suffix`       VARCHAR(32)  DEFAULT NULL COMMENT '后缀名（不含点）',
    `user_id`           BIGINT       NOT NULL COMMENT '所属用户',
    `parent_id`         BIGINT       NOT NULL DEFAULT 0 COMMENT '父目录 ID，0 表示根目录',
    `isFolder`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否文件夹',
    `isDeleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `local_file_path`   VARCHAR(512) DEFAULT NULL COMMENT '本地存储路径（非 OSS 场景）',
    `created_at`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at`        DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted_at`        DATETIME     DEFAULT NULL COMMENT '移入回收站时间',
    `isStarred`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否收藏',
    `starred_at`        DATETIME     DEFAULT NULL COMMENT '收藏时间',
    `category_id`       INT          DEFAULT NULL COMMENT '分类 ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_parent` (`user_id`, `parent_id`),
    KEY `idx_sha256` (`sha256_hash`),
    KEY `idx_user_deleted` (`user_id`, `isDeleted`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_user_starred` (`user_id`, `isStarred`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件与文件夹元信息';

-- ------------------------------------------------------------
-- 最近访问（file-manage 写，下载前回调）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_recent_access`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
    `file_id`     BIGINT   NOT NULL COMMENT '文件 ID',
    `accessed_at` DATETIME DEFAULT NULL COMMENT '最近访问时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_file` (`user_id`, `file_id`),
    KEY `idx_user_time` (`user_id`, `accessed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='最近访问记录';

-- ------------------------------------------------------------
-- 分享记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_share_record`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_id`     BIGINT      NOT NULL COMMENT '被分享的文件 ID',
    `user_id`     BIGINT      NOT NULL COMMENT '分享人',
    `share_code`  VARCHAR(32) NOT NULL COMMENT '分享码',
    `password`    VARCHAR(64) DEFAULT NULL COMMENT '提取码（可为空表示无码）',
    `expire_time` DATETIME    DEFAULT NULL COMMENT '过期时间，NULL 表示永久',
    `create_time` DATETIME    DEFAULT NULL COMMENT '创建时间',
    `is_expired`  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已过期（定时任务标记）',
    `visit_count` INT         NOT NULL DEFAULT 0 COMMENT '访问次数',
    `created_at`  DATETIME    DEFAULT NULL COMMENT '创建时间（历史字段，保留兼容）',
    `updated_at`  DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_code` (`share_code`),
    KEY `idx_user` (`user_id`),
    KEY `idx_expire` (`is_expired`, `expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文件分享';

-- ------------------------------------------------------------
-- 操作日志（由 MQ 异步落库，msg_id 保证消费幂等）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户',
    `operation`   VARCHAR(32)  DEFAULT NULL COMMENT 'UPLOAD/DELETE/RENAME/SHARE_CREATE/SHARE_CANCEL',
    `file_id`     BIGINT       DEFAULT NULL COMMENT '文件 ID',
    `filename`    VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    `file_size`   BIGINT       DEFAULT NULL COMMENT '文件大小（字节）',
    `extra_info`  VARCHAR(512) DEFAULT NULL COMMENT '附加信息',
    `client_ip`   VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `msg_id`      VARCHAR(64)  DEFAULT NULL COMMENT '生产端消息 ID，消费幂等键',
    `create_time` DATETIME     DEFAULT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`msg_id`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';

-- ------------------------------------------------------------
-- AI 会话
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_conversation`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '所属用户',
    `title`         VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `mode`          VARCHAR(32)  DEFAULT NULL COMMENT '对话模式（chat / agent）',
    `model`         VARCHAR(64)  DEFAULT NULL COMMENT '使用的模型',
    `count`         BIGINT       NOT NULL DEFAULT 0 COMMENT '消息条数',
    `total_tokens`  BIGINT       NOT NULL DEFAULT 0 COMMENT '累计 token 消耗',
    `memory_state`  VARCHAR(64)  DEFAULT NULL COMMENT '会话记忆状态',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '最后活跃时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `update_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 会话';

-- ------------------------------------------------------------
-- AI 消息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_message`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id` BIGINT      NOT NULL COMMENT '会话 ID',
    `role`            VARCHAR(16) NOT NULL COMMENT 'user / assistant / system / tool',
    `content`         MEDIUMTEXT COMMENT '消息正文',
    `input_tokens`    INT         NOT NULL DEFAULT 0 COMMENT '输入 token',
    `output_tokens`   INT         NOT NULL DEFAULT 0 COMMENT '输出 token',
    `create_time`     DATETIME    DEFAULT NULL COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`conversation_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 消息';

-- ------------------------------------------------------------
-- AI 用量与成本
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_ai_usage_log`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT      DEFAULT NULL COMMENT '用户 ID',
    `conversation_id`   BIGINT      DEFAULT NULL COMMENT '会话 ID',
    `model`             VARCHAR(64) DEFAULT NULL COMMENT '模型名',
    `scene`             VARCHAR(64) DEFAULT NULL COMMENT '调用场景',
    `prompt_tokens`     INT         NOT NULL DEFAULT 0 COMMENT '输入 token',
    `completion_tokens` INT         NOT NULL DEFAULT 0 COMMENT '输出 token',
    `total_tokens`      INT         NOT NULL DEFAULT 0 COMMENT '总 token',
    `tool_call_count`   INT         NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    `latency_ms`        BIGINT      NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `created_at`        DATETIME    DEFAULT NULL COMMENT '调用时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `created_at`),
    KEY `idx_scene_time` (`scene`, `created_at`),
    KEY `idx_model_time` (`model`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 用量成本';
