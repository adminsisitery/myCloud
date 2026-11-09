-- ============================================================
-- V1（pgvector）：AI 向量库初始 schema
-- ============================================================
-- 执行者：ai-service（PG 只有它用）
-- 说明：PG 与 MySQL 各自维护独立的 flyway_schema_history，版本号互不影响，
--       所以这里也是 V1（清单里写的 V2 是「两个库合起来编号」的视角）。
--
-- 与 langchain4j PgVectorEmbeddingStore 的 auto-init 保持一致：
-- 表结构一致，先由迁移建好，服务启动时的 initTable() 就是幂等空操作，
-- 这样 Testcontainers 才不用等应用把表建出来。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS file_segment
(
    embedding_id uuid    NOT NULL,
    -- 维度与 ai.embedding-dimensions 一致，默认 1536
    embedding    vector(1536) NOT NULL,
    text         text,
    metadata     jsonb,
    PRIMARY KEY (embedding_id)
);

-- 按 metadata 过滤（userId / fileId）走这个索引，避免全表扫
CREATE INDEX IF NOT EXISTS idx_file_segment_metadata ON file_segment USING gin (metadata);

COMMENT ON TABLE file_segment IS 'AI 检索用文件片段向量（langchain4j PgVectorEmbeddingStore）';
