# Sentinel 熔断接入说明

只做熔断（degrade），不做流控。熔断对象是服务间 Feign 调用。

## 一、需要放到 Nacos 的配置

| Data ID | Group | 动作 |
|---|---|---|
| `pan-common` | `pan` | 覆盖，内容见 `nacos-config/pan-common.yaml` |
| `pan-Sentinel` | `pan` | 覆盖，内容见 `nacos-config/pan-Sentinel.yaml` |
| `ai-service-degrade-rules` | `SENTINEL_GROUP` | 新建，有熔断规则 |
| `file-manage-service-degrade-rules` | `SENTINEL_GROUP` | 新建，有熔断规则 |
| `file-task-service-degrade-rules` | `SENTINEL_GROUP` | 新建，有熔断规则 |
| `file-upload-service-degrade-rules` | `SENTINEL_GROUP` | 新建，有熔断规则 |
| `file-download-service-degrade-rules` | `SENTINEL_GROUP` | 新建，有熔断规则 |
| `file-async-service-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |
| `file-preview-service-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |
| `file-search-service-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |
| `file-share-service-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |
| `user-service-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |
| `gateway-degrade-rules` | `SENTINEL_GROUP` | 新建，内容 `[]` |

后六个服务没有 Feign 调用，暂时没有熔断对象，占位 `[]`。留着是因为所有服务都 import 了 `pan-Sentinel`，`data-id` 是按 `${spring.application.name}` 拼的，缺了会读不到。以后这些服务加了 Feign 调用，直接往对应文件里补规则即可。

网关的熔断对象是路由，规则类型是 `gw-degrade` 而不是 `degrade`。要做网关熔断得单独加一个 datasource 配置项，本次没做。

覆盖 `pan-common` 前先比对线上内容，那份文件还承载着 `server.shutdown`、日志级别、内部调用地址等配置。

## 二、规则为什么放在 `SENTINEL_GROUP`

哨兵规则和业务配置分开组管理，避免业务配置误删。group 名要与 `pan-Sentinel.yaml` 里的 `group-id` 一致。

## 三、dataId 为什么按服务拆开

`data-id: ${spring.application.name}-degrade-rules`。一个服务一份，各自独立演进：改 `file-upload-service` 的阈值不会影响 `ai-service`，排查时也能直接看出是哪个服务下的规则。

如果所有服务共用一个 dataId，任何一次调整都会推给全部服务，等于把 11 个服务的熔断参数耦在一起。

## 四、资源名格式

Sentinel 对 Feign 的资源名是 `HTTP方法:targetURL路径` 三段拼成：

```
POST:http://user-service/internal/space/reserve
GET:http://user-service/internal/space/info
POST:http://file-manage-service/internal/recent/record
```

服务名来自 `@FeignClient(name = "user-service", path = "/internal/space")` 的 name 和 path，不是 Nacos 的实例地址。部署到容器里也不会变。

写规则时 `resource` 必须完全一致，差一个字符规则就不生效。确认办法：应用起来后看 Dashboard 的「簇点链路」，里面列出的就是真实资源名。

## 五、规则参数含义

每个资源配了两条规则：

| grade | 类型 | count 含义 | 其它 |
|---|---|---|---|
| 0 | 慢调用比例 | 响应时间阈值（毫秒），这里是 2000 | `slowRatioThreshold: 0.5` 表示慢调用占比超过 50% |
| 1 | 异常比例 | 异常占比阈值（0~1），这里是 0.5 | 不配 `slowRatioThreshold` |

公共字段：

- `timeWindow: 10` —— 熔断持续 10 秒，之后进入半开状态放一个请求试探
- `minRequestAmount: 5` —— 统计窗口内请求数不足 5 条不触发熔断，防止低流量误判
- `statIntervalMs: 10000` —— 统计窗口 10 秒

两条规则是"或"关系：任一满足即熔断。

## 六、超时是熔断的前提

`pan-common.yaml` 里配了：

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 2000
        readTimeout: 5000
```

默认的 `readTimeout` 是 60 秒。不收紧的话出现两种后果：慢调用统计里几乎没有「慢」的样本（因为都还没到 60 秒），以及下游卡住时上游线程先被占满，熔断还没触发就已经雪崩。

慢调用阈值 2000ms 要小于 `readTimeout` 5000ms，否则永远统计不到慢调用。

## 七、降级行为

`fallbackFactory` 而不是 `fallback`，因为要拿到异常做日志。

| 方法 | 降级返回 | 理由 |
|---|---|---|
| `reserveSpace` | `false` | 调用方都是 `if (!reserveSpace(...))` 判断，返回 false 走拒绝分支。宁可拒绝这次写入，也不能让配额超卖 |
| `releaseSpace` | 空实现 | 补偿操作，失败只记日志，不阻断用户操作 |
| `addUsedSpace` | 空实现 | 同上 |
| `getSpaceUsage` | `null` | 调用方已做判空（`if (usage == null \|\| usage.totalSpace() <= 0) return;`） |
| `recordAccess` | 空实现 | 旁路埋点，本身就不该影响下载 |

`file-download-service` 里另外给 `recordAccess` 加了 try-catch。原先没有，内部接口一挂下载就失败，与它自己注释写的「不阻断下载」相反。fallback 只覆盖熔断场景，try-catch 才是兜底，两者不冲突。

## 八、验证

**1. 确认开关生效**

启动任一服务，日志里应有：

```
[Sentinel Starter] register ... to Sentinel dashboard
```

没有这行说明 `feign.sentinel.enabled` 或 `transport.dashboard` 没读到。

**2. 确认规则加载**

Dashboard 左侧「流控规则 / 熔断规则」里能看到 `POST:http://user-service/...` 这些资源。

若为空，说明 datasource 的 dataId 或 group 对不上。

**3. 手动触发熔断**

把 `user-service` 停掉，连续调 6 次上传接口（`minRequestAmount` 是 5），第 6 次开始应该走 fallback：接口返回「配额不足」而不是 500。

Dashboard 上该资源的熔断状态会变成 OPEN。

## 九、注意：Dashboard 改的规则不会写回 Nacos

开源版 Dashboard 只能把规则推到客户端内存，没有回写 Nacos 的能力。所以：

- 在 Dashboard 上调完参数看着生效了，重启应用就从 Nacos 拉回旧值
- 要固化，必须直接改 Nacos 上那份 JSON

规则以 Nacos 为准，Dashboard 只用来观察和临时试参数。
