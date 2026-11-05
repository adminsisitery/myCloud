
#!/bin/bash
# ============================================================
# 服务器端初始化脚本 —— 在 47.95.192.221 上跑一次即可
# 用法: ssh root@47.95.192.221 'bash -s' < deploy/server-setup.sh
# ============================================================
set -e

echo ">>> 创建目录"
mkdir -p /opt/mycloud/logs

echo ">>> 检查 Java"
java -version 2>&1 || { echo "需要 JDK 21!"; exit 1; }

echo ">>> 检查中间件连通性"
echo -n "  Nacos   : "; curl -s -o /dev/null -w "%{http_code}" http://localhost:8848/nacos/ && echo " OK" || echo " FAIL"
echo -n "  Redis   : "; redis-cli ping 2>/dev/null && echo " OK" || echo " FAIL"
echo -n "  MySQL   : "; mysqladmin -h rm-bp1k9t18kfewh9780bo.mysql.rds.aliyuncs.com -u root -p'LPF2005525a' ping 2>/dev/null && echo " OK" || echo " FAIL"
echo -n "  RMQ     : "; curl -s -o /dev/null -w "%{http_code}" http://localhost:15672/ | grep -q "200\|401" && echo " OK" || echo " FAIL"
echo -n "  ES      : "; curl -s -o /dev/null -w "%{http_code}" http://localhost:9200/ | grep -q "200\|401" && echo " OK" || echo " FAIL"
echo -n "  PG      : "; pg_isready -h localhost -p 5432 -U postgres 2>/dev/null && echo " OK" || echo " (pg_isready 未装,跳过)"
echo ""

echo ">>> 检查内存"
free -h

echo ">>> 初始化完成。下一步：本地运行 deploy/deploy.sh 上传 jar 并启动服务"