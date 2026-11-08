#!/usr/bin/env python3
"""死信队列人工重投工具。

按消息的 x-death 头判断原始队列，把死信发回对应交换机与路由键。
默认 dry-run，只打印不投递；确认无误后加 --apply 真正重投。

用法：
    python dlq_requeue.py                 # 预览
    python dlq_requeue.py --apply         # 执行
    python dlq_requeue.py --apply --limit 500

依赖：只用标准库。需要开启 RabbitMQ management 插件（默认 15672 端口）。
"""

import argparse
import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

# 来源队列 -> (交换机, 路由键)，与 RabbitMqTopologyConfig 保持一致
ROUTES = {
    "log.queue": ("log.topic", "operation_log"),
    "usage:queue": ("usage:exchange", "usage"),
    "folder.download.queue": ("folder.download.topic", "folder.download.create"),
}


def api(method, url, auth, payload=None, timeout=10):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Basic " + auth)
    if data:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode()
        return json.loads(body) if body else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:15672/api")
    parser.add_argument("--user", default="guest")
    parser.add_argument("--password", default="guest")
    parser.add_argument("--vhost", default="/")
    parser.add_argument("--queue", default="dlx:queue")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--apply", action="store_true", help="真正重投，默认只预览")
    args = parser.parse_args()

    auth = base64.b64encode(f"{args.user}:{args.password}".encode()).decode()
    vhost = urllib.parse.quote(args.vhost, safe="")
    base = f"{args.api}/queues/{vhost}/{urllib.parse.quote(args.queue, safe='')}"

    messages = api("POST", base + "/get", auth,
                   {"count": args.limit, "ackmode": "ack_requeue_false", "encoding": "auto"})
    if not messages:
        print("死信队列为空")
        return 0

    print(f"取到 {len(messages)} 条死信")
    stats = {"requeued": 0, "skipped": 0, "failed": 0}

    for msg in messages:
        props = msg.get("properties", {})
        headers = props.get("headers", {}) or {}
        x_death = headers.get("x-death") or []
        if not x_death:
            print(f"[跳过] 无 x-death 头，消息体前 80 字符: {str(msg.get('payload'))[:80]}")
            stats["skipped"] += 1
            continue

        origin = x_death[0].get("queue")
        count = x_death[0].get("count", 0)
        route = ROUTES.get(origin)
        if route is None:
            print(f"[跳过] 未知来源队列 {origin}，重投次数 {count}")
            stats["skipped"] += 1
            continue

        exchange, routing_key = route
        print(f"[重投] {origin} -> {exchange}/{routing_key} (第 {count} 次) msgId={props.get('message_id')}")

        if not args.apply:
            stats["requeued"] += 1
            continue

        try:
            api("POST", f"{args.api}/exchanges/{vhost}/{urllib.parse.quote(exchange, safe='')}/publish", auth, {
                "properties": props,
                "routing_key": routing_key,
                "payload": msg.get("payload", ""),
                "payload_encoding": msg.get("payload_encoding", "string"),
            })
            stats["requeued"] += 1
        except urllib.error.HTTPError as e:
            print(f"[失败] 投递异常 {e.code} {e.read().decode()[:200]}")
            stats["failed"] += 1

    mode = "已执行" if args.apply else "预览（加 --apply 执行）"
    print(f"{mode}：重投 {stats['requeued']}，跳过 {stats['skipped']}，失败 {stats['failed']}")
    return 1 if stats["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
