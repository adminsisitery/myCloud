#!/usr/bin/env bash
# ============================================================
# 给 PostgreSQL 应用 ai-service 的向量库迁移（P0-6 的 PG 半边）
# ============================================================
# 为什么不用 Flyway：ai-service 的 PG 连接是 langchain4j 自己管的
# （PgVectorEmbeddingStore 直接拿 host/port/password 建连），
# 没有 Spring DataSource，Flyway 的自动迁移挂不上去。
# 所以 PG 侧用一个独立的幂等脚本执行同一批 SQL。
#
# 用法：
#   PGHOST=127.0.0.1 PGPORT=5432 PGDATABASE=mycloud \
#   PGUSER=mycloud PGPASSWORD=*** ./pg_migrate.sh
#
# 脚本可重复执行：迁移文件里全是 CREATE ... IF NOT EXISTS。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="${MIGRATION_DIR:-$SCRIPT_DIR/../../ai-service/src/main/resources/db/migration-pg}"

: "${PGHOST:?请设置 PGHOST}"
: "${PGDATABASE:?请设置 PGDATABASE}"
: "${PGUSER:?请设置 PGUSER}"
: "${PGPASSWORD:?请设置 PGPASSWORD 或使用 .pgpass}"

if [ ! -d "$MIGRATION_DIR" ]; then
  echo "找不到迁移目录: $MIGRATION_DIR" >&2
  exit 1
fi

export PGPASSWORD

for sql in "$MIGRATION_DIR"/*.sql; do
  [ -e "$sql" ] || { echo "迁移目录里没有 .sql 文件"; exit 1; }
  echo "==> 应用 $(basename "$sql")"
  psql -v ON_ERROR_STOP=1 -h "$PGHOST" -p "${PGPORT:-5432}" -U "$PGUSER" -d "$PGDATABASE" -f "$sql"
done

echo "PG 迁移完成"
psql -h "$PGHOST" -p "${PGPORT:-5432}" -U "$PGUSER" -d "$PGDATABASE" -c "\d file_segment" | head -20
