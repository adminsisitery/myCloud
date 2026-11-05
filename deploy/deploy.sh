#!/bin/bash
# ============================================================
# myCloud 一键部署脚本
# 用法:
#   ./deploy.sh           构建全部 11 个服务 + 上传 + 重启
#   ./deploy.sh build     只构建，不上传
#   ./deploy.sh upload    只上传 + 重启（jar 已构建好）
#   ./deploy.sh restart   只重启（jar 已在服务器上）
#   ./deploy.sh <service> 只构建+上传+重启指定服务（如 gateway）
# ============================================================

set -e

# 自动定位到项目根目录（无论从哪里执行）
cd "$(dirname "$0")/.."
PROJECT_ROOT="$(pwd)"

SERVER="root@47.95.192.221"
SERVER_DIR="/opt/mycloud"
JAVA_HOME="${JAVA_HOME:-/f/java}"
MAVEN_OPTS="-DskipTests"

# 所有服务: 模块目录=jar名称=systemd单元名
declare -A SERVICES=(
  ["gateway"]="pan-gateway|pan-gateway|mycloud-gateway"
  ["user"]="user-service|user-service|mycloud-user-service"
  ["upload"]="file-upload-service|file-upload-service|mycloud-file-upload"
  ["download"]="file-download-service|file-download-service|mycloud-file-download"
  ["manage"]="file-manage-service|file-manage-service|mycloud-file-manage"
  ["share"]="file-share-service|file-share-service|mycloud-file-share"
  ["async"]="file-async-service|file-async-service|mycloud-file-async"
  ["preview"]="file-preview-service|file-preview-service|mycloud-file-preview"
  ["ai"]="ai-service|ai-service|mycloud-ai-service"
  ["search"]="file-search-service|file-search-service|mycloud-file-search"
  ["task"]="file-task-service|file-task-service|mycloud-file-task"
)

ALL_SERVICES="gateway user upload download manage share async preview ai search task"

# ---- 颜色 ----
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERR]${NC} $*"; }

MODULES=""
SVC_LIST=""

parse_services() {
  if [ $# -eq 0 ] || [ "$1" = "all" ]; then
    SVC_LIST=$ALL_SERVICES
    MODULES=$(for s in $ALL_SERVICES; do echo "${SERVICES[$s]}" | cut -d'|' -f1; done | sort -u | tr '\n' ' ')
  else
    SVC_LIST="$*"
    MODULES=$(for s in $*; do
      entry="${SERVICES[$s]}"
      [ -z "$entry" ] && err "未知服务: $s (可用: $ALL_SERVICES)" && exit 1
      echo "$entry" | cut -d'|' -f1
    done | sort -u | tr '\n' ' ')
  fi
  log "目标服务: $SVC_LIST"
  log "Maven模块: $MODULES"
}

# ---- 构建 ----
build_jars() {
  log "开始构建..."
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "JAVA_HOME=$JAVA_HOME"
  java -version 2>&1 | head -1

  mvn $MAVEN_OPTS clean package -pl pan-common -am -q
  log "pan-common 构建完成"

  # 并行构建所有服务模块
  mvn $MAVEN_OPTS -pl $(echo $MODULES | tr ' ' ',') -am package -q
  log "全部 service jar 构建完成"

  # 列出产物
  for mod in $MODULES; do
    jar="$mod/target/${mod}-1.0-SNAPSHOT.jar"
    if [ -f "$jar" ]; then
      printf "  %-35s %6s MB\n" "$mod" "$(du -m "$jar" | cut -f1)"
    else
      err "产物缺失: $jar"
      exit 1
    fi
  done
}

# ---- 上传 ----
upload_files() {
  log "上传 jar 到 $SERVER:$SERVER_DIR ..."

  # 收集所有 jar 路径
  jars=""
  for svc in $SVC_LIST; do
    mod=$(echo "${SERVICES[$svc]}" | cut -d'|' -f1)
    jars="$jars $mod/target/${mod}-1.0-SNAPSHOT.jar"
  done

  scp $jars $SERVER:$SERVER_DIR/

  log "上传 systemd unit 文件..."
  for svc in $SVC_LIST; do
    unit=$(echo "${SERVICES[$svc]}" | cut -d'|' -f3)
    scp "deploy/systemd/${unit}.service" $SERVER:/etc/systemd/system/
  done

  log "上传 .env ..."
  scp deploy/.env $SERVER:$SERVER_DIR/

  log "上传完成"
}

# ---- 远程重启 ----
restart_services() {
  log "远程重启服务..."

  # 收集要重启的 unit 名
  units=""
  for svc in $SVC_LIST; do
    unit=$(echo "${SERVICES[$svc]}" | cut -d'|' -f3)
    units="$units $unit"
  done

  ssh $SERVER bash <<SSH_SCRIPT
set -e
echo ">>> 创建日志目录"
mkdir -p /opt/mycloud/logs

echo ">>> 重新加载 systemd"
systemctl daemon-reload

# 逐个启动（不并行，避免内存尖峰）
for unit in $units; do
  echo "--- \$unit ---"
  systemctl stop  \$unit 2>/dev/null || true
  sleep 2
  systemctl start \$unit
  sleep 5

  # 快速检查是否还活着
  if systemctl is-active --quiet \$unit; then
    echo "  \$unit : RUNNING"
  else
    echo "  \$unit : FAILED — 看日志: journalctl -u \$unit --no-pager -n 20"
  fi
done

echo ""
echo "==================== 服务状态 ===================="
for unit in $units; do
  status=\$(systemctl is-active \$unit 2>/dev/null || echo "dead")
  printf "  %-35s %s\n" "\$unit" "\$status"
done

echo ""
echo "==================== 内存使用 ===================="
free -h | grep -E "Mem|Swap"
SSH_SCRIPT

  log "重启完成"
}

# ---- 主流程 ----
STEP="${1:-all}"

case "$STEP" in
  build)
    parse_services "${@:2}"
    build_jars
    ;;
  upload)
    parse_services "${@:2}"
    upload_files
    restart_services
    ;;
  restart)
    parse_services "${@:2}"
    restart_services
    ;;
  *)
    parse_services "${@:2}"
    build_jars
    upload_files
    restart_services
    ;;
esac

log "部署完成 ✓"