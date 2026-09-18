-- Optional performance migration for duplicate lookup on MySQL 8; does not change file data.
SET @agent_hash_index = IF(
  (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_file_info' AND index_name='ix_agent_user_hash')=0,
  'CREATE INDEX ix_agent_user_hash ON t_file_info(user_id,isDeleted,isFolder,sha256_hash)', 'SELECT 1');
PREPARE agent_hash_stmt FROM @agent_hash_index;
EXECUTE agent_hash_stmt;
DEALLOCATE PREPARE agent_hash_stmt;
