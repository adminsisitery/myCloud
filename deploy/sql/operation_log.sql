-- operation_log 建表（含消费幂等唯一索引）
-- 已有表的环境执行文件末尾的 ALTER 即可，不要重复建表

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户',
  `operation`   VARCHAR(32)  DEFAULT NULL COMMENT '操作类型：UPLOAD/DELETE/RENAME/SHARE_CREATE/SHARE_CANCEL',
  `file_id`     BIGINT       DEFAULT NULL COMMENT '文件ID',
  `filename`    VARCHAR(255) DEFAULT NULL COMMENT '文件名',
  `file_size`   BIGINT       DEFAULT NULL COMMENT '文件大小（字节）',
  `extra_info`  VARCHAR(512) DEFAULT NULL COMMENT '附加信息',
  `client_ip`   VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
  `msg_id`      VARCHAR(64)  DEFAULT NULL COMMENT '生产端消息ID，消费幂等键',
  `create_time` DATETIME     DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

-- 已有表升级（列或索引已存在时跳过对应语句）
-- ALTER TABLE `operation_log`
--   ADD COLUMN `msg_id` VARCHAR(64) DEFAULT NULL COMMENT '生产端消息ID，消费幂等键' AFTER `client_ip`;

-- ALTER TABLE `operation_log`
--   ADD UNIQUE KEY `uk_msg_id` (`msg_id`);
