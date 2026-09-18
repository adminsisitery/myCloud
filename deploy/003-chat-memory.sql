-- MySQL 8. Additive migration; run after 001-agent.sql. No history is deleted.
CREATE TABLE IF NOT EXISTS t_ai_chat_memory (
  conversation_id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  summary MEDIUMTEXT,
  through_message_id BIGINT NOT NULL DEFAULT 0,
  history_gap BOOLEAN NOT NULL DEFAULT FALSE,
  lease_id CHAR(36),
  lease_until DATETIME,
  KEY ix_chat_memory_user (user_id,conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_ai_chat_turn (
  id CHAR(36) NOT NULL PRIMARY KEY,
  request_id VARCHAR(80) NOT NULL,
  user_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  user_message_id BIGINT NOT NULL,
  assistant_message_id BIGINT,
  status VARCHAR(24) NOT NULL,
  messages MEDIUMTEXT,
  error_code VARCHAR(80),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_turn_request (user_id,request_id),
  KEY ix_chat_turn_history (user_id,conversation_id,user_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
