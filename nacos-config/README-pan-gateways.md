# ============================================================
# Nacos 配置下发说明 —— 把 pan-gateways.yaml 贴到 Nacos
#
#   Data ID : pan-gateways
#   Group   : pan
#   格式    : YAML
#
# 步骤：
#   1. Nacos 控制台 → 配置管理 → 配置列表 → 找到 pan-gateways（group=pan）
#   2. 点「编辑」，把 nacos-config/pan-gateways.yaml 全文粘进去（整体替换）
#   3. 点「发布」
#   4. 重启网关：docker restart mycloud-gateway
#      或 systemctl restart mycloud-gateway
#
# 注意：
#   - 这份配置已经包含原有 9 条路由，是"整体替换"不是"追加"
#   - 上线前把 allowed-origin-patterns 里的生产域名取消注释并改掉
#   - 配置只在 Nacos 一处维护，代码里不再有 globalcors
# ============================================================
