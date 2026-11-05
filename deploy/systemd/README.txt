# myCloud 微服务 systemd unit 文件
# 放到 /etc/systemd/system/ 后执行:
#   systemctl daemon-reload
#   systemctl enable mycloud-gateway mycloud-user-service ...
#   systemctl start mycloud-gateway mycloud-user-service ...

# ============================================================
# 每个服务统一 JVM 参数（11 × ~320MB RSS ≈ 3.5GB）
# ============================================================
# -Xms128m -Xmx192m   堆 128-192M（实测够用）
# -XX:+UseG1GC         G1 低停顿
# -XX:MaxGCPauseMillis=200
# -XX:+HeapDumpOnOutOfMemoryError  OOM 时 dump
# -XX:HeapDumpPath=/opt/mycloud/logs