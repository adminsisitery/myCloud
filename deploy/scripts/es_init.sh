#!/usr/bin/env bash
# ============================================================
# 创建 ES 索引 file_index（P0-6 里 ES mapping 的落地点）
# ============================================================
# 幂等：索引已存在就只补 mapping（PUT /_mapping 不会覆盖已有字段，
# 只追加新字段），所以重复执行安全。
#
# 用法：
#   ES_URL=http://127.0.0.1:9200 ./es_init.sh
#   带鉴权时：ES_USER=elastic ES_PASSWORD=*** ./es_init.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAPPING_FILE="${MAPPING_FILE:-$SCRIPT_DIR/../es/file_index.json}"
ES_URL="${ES_URL:-http://127.0.0.1:9200}"
INDEX="${ES_INDEX:-file_index}"

if [ ! -f "$MAPPING_FILE" ]; then
  echo "找不到 mapping 文件: $MAPPING_FILE" >&2
  exit 1
fi

AUTH=()
if [ -n "${ES_USER:-}" ] && [ -n "${ES_PASSWORD:-}" ]; then
  AUTH=(-u "$ES_USER:$ES_PASSWORD")
fi

code=$(curl -s -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$ES_URL/$INDEX")

if [ "$code" = "200" ]; then
  echo "索引 $INDEX 已存在，追加 mapping"
  curl -fsS "${AUTH[@]}" -X PUT "$ES_URL/$INDEX/_mapping" \
    -H 'Content-Type: application/json' \
    --data-binary @"$MAPPING_FILE" >/dev/null
else
  echo "创建索引 $INDEX"
  curl -fsS "${AUTH[@]}" -X PUT "$ES_URL/$INDEX" \
    -H 'Content-Type: application/json' \
    --data-binary @"$MAPPING_FILE" >/dev/null
fi

echo "完成：$ES_URL/$INDEX"
