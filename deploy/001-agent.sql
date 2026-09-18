-- MySQL 8. Run once per release against the EXISTING myCloud database, after backup.
-- Creates missing AI tables; does not create or replace the cloud-drive user/file schema.
CREATE TABLE IF NOT EXISTS t_ai_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  model VARCHAR(100),
  mode VARCHAR(16) NOT NULL DEFAULT 'CHAT',
  count BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  memory_state MEDIUMTEXT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_ai_conversation_user (user_id,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  input_tokens INT,
  output_tokens INT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_ai_message_conversation (conversation_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_usage_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  conversation_id BIGINT,
  model VARCHAR(100) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  tool_call_count INT NOT NULL DEFAULT 0,
  latency_ms BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_ai_usage_user (user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_agent_run (
  id CHAR(36) NOT NULL PRIMARY KEY,
  request_id VARCHAR(80) NOT NULL,
  user_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  model VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL,
  snapshot MEDIUMTEXT NOT NULL,
  cancel_requested TINYINT NOT NULL DEFAULT 0,
  finalized TINYINT NOT NULL DEFAULT 0,
  deadline DATETIME NOT NULL,
  active_user BIGINT NULL,
  active_conversation BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_active_user (active_user),
  UNIQUE KEY uk_agent_request (user_id,request_id),
  UNIQUE KEY uk_agent_active_conversation (active_conversation),
  KEY ix_agent_history (user_id,conversation_id,created_at),
  KEY ix_agent_deadline (status,deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_agent_action (
  id CHAR(36) NOT NULL PRIMARY KEY,
  run_id CHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  fingerprint CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  snapshot MEDIUMTEXT NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_action_fingerprint (run_id,fingerprint),
  KEY ix_agent_action_user (user_id,run_id),
  KEY ix_agent_action_expiry (status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Idempotent upgrade of the pre-Agent conversation table.
SET @agent_mode_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_ai_conversation' AND column_name='mode')=0,
  'ALTER TABLE t_ai_conversation ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT ''CHAT''', 'SELECT 1');
PREPARE agent_mode_stmt FROM @agent_mode_ddl;
EXECUTE agent_mode_stmt;
DEALLOCATE PREPARE agent_mode_stmt;
