# MyCloud 企业级改造清单

> 核查日期：2026-11-08　基线：393 个 Java 文件 / 12 模块 / 16 个测试文件 / 0 个 `@SpringBootTest`
>
> 每条格式：**改什么 → 为什么改 → 改完什么效果**。按优先级排序，P0 未完成前不要动 P1。

---

## 完成状态（2026-11-09）

> 提交号按重写后的历史更新：`/docs/` 入库时清掉了旧提交里的明文密钥，后续 P0-3/P0-4/P0-6 是在新历史上重新落盘的。

| 条目 | 状态 | 提交 | 验收方式 |
|---|---|---|---|
| P0-2 `docs/` 入库 | ✅ 完成 | `c13fc74` | 6 份文档入库；入库前脱敏了 Nacos IP/密码、auth token secret key、Woodpecker secret、测试账号密码 |
| P0-3 `/internal/**` 鉴权 | ✅ 完成 | `f1cf4a9` | pan-common 拦截器（常量时间比较 + ±60s 防重放）+ 全局 Feign 注入；token 缺失一律 401；12 个服务编译通过 |
| P0-4 端口收敛 | ✅ 完成 | `be8be9c` | 12 服务只走 bridge；新增 `docker-compose.middleware.yml`，6 个中间件零 `ports`，`docker compose config` 实测只有 gateway 发布 8090 |
| P0-5 网关 CORS + 限流 | ✅ CORS 完成 | `82cdf68` | 预检 200、白名单外 403；限流已有 `@RateLimit`，全局限流待补 |
| P0-6 Flyway | ✅ 完成 | `84725f9` | `V1__init_mysql.sql` 9 张表，本机 MySQL 8 实跑通过 + 幂等；PG/ES 各有脚本 |
| 审计门禁 | ✅ 完成 | `20a2039` | 仓库变量 `MYRC_FAIL_ON=high`；基线 2027 条；CI 连跑稳定 0 问题 |
| P1-2 Testcontainers | ⏳ 可开始 | — | P0-6 已解除阻塞 |

**P0 剩余**：P0-1（`.gitignore` 补漏）、P0-5 的全局限流、P0-7（CI 收敛，实际已只剩 GitHub Actions 一套）。

### 审计门禁的两个坑（已修，别再踩）

1. **报告不能落在工作区里**。`--output myrc-report.json` 写在仓库根目录，下一轮审计会把它当源码扫，文件集从 509 变成 510，扫描器结果随之漂移。现在两份报告都写 `$RUNNER_TEMP`。
2. **门禁只能由一次调用判定**。同一环境连跑两次（JSON + SARIF），gitleaks 的历史扫描一次 7 条、一次 8 条，构建一绿一红。现在只有 JSON 那次带 `--fail-on`，SARIF 降级为 `continue-on-error` 的展示步骤。

### 仍然存在的暴露（需人工处理，非代码可解）

`gitleaks` 在 git 历史里查到 **7 处真实密钥**，仓库是 public，等于已经公开：

| 位置（历史提交） | 配置项 | 需要动作 |
|---|---|---|
| `src/main/resources/application.yml:135`（`8c5855a`） | `api-key`（DeepSeek） | 去 DeepSeek 控制台吊销重签 |
| `src/main/resources/application.yml:141`（`8c5855a`） | `api-key`（DashScope） | 去阿里百炼吊销重签 |
| `src/main/resources/application.yml:135`（`f9d38c5`） | `embedding-api-key`（DashScope） | 同上 |
| `src/main/resources/local_application.yml:3`（`ebde68e`） | `api-key` | 确认归属后吊销 |
| `src/main/resources/application.yml:246`（`10a63ae`） | `secret-key`（Langfuse） | 去 Langfuse 轮换 |
| `src/main/resources/application.yml:91`（`2d81612`） | `client-secret`（GitHub OAuth） | GitHub OAuth App 重新生成 |
| `deploy/woodpecker/docker-compose.yml:19`（`168482d`） | `WOODPECKER_GITEA_SECRET` | Woodpecker/Gitea 重置 |

这些指纹已在基线里，所以 CI 不会报警——**但这只代表不再打扰，不代表安全**。轮换完成后基线里的对应条目可以删掉；想彻底从历史抹掉需要 `git filter-repo`，代价是所有协作者重新 clone，且 GitHub 上的 fork/缓存仍可能残留，所以**轮换优先于重写历史**。

---

## 优先级说明

| 级别 | 含义 | 时间窗口 |
|---|---|---|
| **P0** | 不做就会出事：数据丢失、越权、泄密、发布事故 | 立即，1 周内 |
| **P1** | 企业级门禁缺失：质量无法证明、故障无法定位 | 2–3 周 |
| **P2** | 工程规范：评审会指出，但不影响交付 | 1 个月内 |
| **P3** | 长期演进：架构层面升级 | 有余力再做 |

---

# P0 —— 止血

## P0-1　`.gitignore` 补漏，清理工作区事故隐患

**改什么**
```gitignore
# 追加
/deploy/jars/
hs_err_pid*.log
replay_pid*.log
*.hprof
```
同时删除工作区现有的 `hs_err_pid153676.log`、`replay_pid163484.log`、`replay_pid153676.log`。

**为什么改**
`deploy/jars/` 现在是 **2.0 GB 未跟踪文件**，`git status` 里以 `??` 出现。任何人一次 `git add .` 就会把 2 GB 的 jar 包推进历史——这几乎不可能再干净移除（需要 `filter-repo` 重写全部分支历史，且所有协作者必须重新 clone）。`hs_err_pid*.log` 是 JVM 崩溃现场 dump，属于调试垃圾。

**改完什么效果**
- 仓库体积维持在 MB 级，`git clone` 从分钟级降到秒级；
- 不会出现「提交了 12 个 jar 包」这类不可逆事故；
- 工作区干净，`git status` 只显示真实代码变更，code review 时不会被噪声淹没。

---

## P0-2　把 `docs/` 从 `.gitignore` 移除

**改什么**
`.gitignore` 第 48 行有 `/docs/`，删掉。确认 `docs/enterprise-roadmap.md`、`docs/AI-CODE-REVIEW-DESIGN.md`、`docs/nacos-auth-setup.md` 与本文档入库。

**为什么改**
这几份文档（包括你之前写的改造路线图）**目前完全不在仓库里**。别人 clone 下来看不到任何设计决策、没有任何部署说明。文档不入库等于没写——一旦本地磁盘丢失就是永久损失，PR 里也无从引用。

**改完什么效果**
- 新成员 clone 后能看到完整的设计与路线，不需要口口相传；
- 文档变更走 PR，可 review、可追溯；
- 路线图能作为验收清单逐条核销。

---

## P0-3　`/internal/**` 增加服务间鉴权

**改什么**
1. 在 `pan-common` 新增 `InternalAuthInterceptor`（`HandlerInterceptor`）+ `@EnableInternalAuth` 自动装配；
2. 请求头携带 `X-Internal-Token`，值为启动时从环境变量 `INTERNAL_TOKEN` 读取的服务间共享密钥；
3. 校验规则：token 匹配 + `X-Internal-Timestamp` 与服务器时间偏差 ≤ 60s（防重放）；
4. 在 `user-service` 的 `InternalSpaceController`、`file-manage-service` 的 `InternalRecentController` 上生效；
5. 调用方（`UserSpaceClient` 等 5 处 Feign/RestClient）统一加拦截器注入 header。

**为什么改**
`InternalSpaceController` 现在的注释原文是「后续如需加密，可加服务间鉴权」。当前实现是**完全裸奔**：

```java
@PostMapping("/reserve")
public Result<Boolean> reserve(@RequestParam("userId") Long userId, ...)
```

任何人只要内网能连到 8081，就能 `POST /internal/space/reserve?userId=1&size=999999999999` 给任意用户刷爆配额，或 `release` 扣光任意用户的空间。同理 `/internal/recent` 可以直接读取**任意用户**的最近访问记录。这是**水平越权（IDOR）**，属于安全漏洞级别，不是「待优化」。

**改完什么效果**
- 未携带合法内部凭证的跨服务请求一律 401；
- 即使攻击者拿到内网访问权限，也无法伪造 userId 操作他人数据；
- 时间戳校验使抓包重放失效。

---

## P0-4　服务端口只绑 `127.0.0.1`，杜绝绕过网关

**改什么**
`deploy/docker-compose.yml` 现在 12 个服务全部 `network_mode: host`，8081–8091 全部裸露在宿主机公网 IP 上。改为：
- 业务服务使用 bridge 网络，只 `expose` 不 `ports`；
- 确需宿主机调试的，绑定为 `127.0.0.1:8081:8081`（前缀加 `127.0.0.1`）；
- 仅 `gateway` 的 8090 对外；
- 中间件同理（MySQL/Redis/RabbitMQ/ES/PG 全部绑 `127.0.0.1`）。

**为什么改**
这是 P0-3 的**同源问题但更严重**。网关 `MyGlobalFilter` 的鉴权逻辑是：

```java
headers.set(USER_ID_HEADER, String.valueOf(userId));
```

即「网关认证一次，之后完全信任 `UserId` 头」。一旦能绕过网关直连 8082，执行：

```bash
curl -H "UserId: 1" http://<服务器IP>:8082/api/files/list
```

就**完整冒充 userId=1 的用户**，无需任何凭证。而且 8081 直连可以调 `/internal/**`，MySQL 3306 也是公网可达——整台机器实际上没有任何边界。

**改完什么效果**
- 攻击面从「12 个服务 + 6 个中间件」收敛到「网关 1 个入口」；
- 中间件无法被外部直连爆破；
- 配合 P0-3，内网被突破也不会立刻造成全量数据泄露。

---

## P0-5　网关补 CORS + 全局限流

**改什么**
1. `pan-gateway` 增加 `CorsWebFilter`（或 `spring.cloud.gateway.globalcors` 配置），白名单化 `allowedOriginPatterns`，`allowCredentials=true`；
2. 接入 `RequestRateLimiter`（Redis 令牌桶）：
   - 全局限流：如 100 req/s/用户；
   - 登录/注册接口单独更严：如 5 次/分钟/IP；
3. 外部流量统一走网关限流，`pan-common` 里的 `RateLimitAspect` 只保留给内部接口用。

**为什么改**
`MyGlobalFilter` 里只有 `StpUtil.checkLogin()` 一行防护。`pan-common` 虽然写了 `RateLimit` 注解和切面，但**网关层没有任何限流**——意味着：

- `/api/user/login` 可以被无限次撞库，密码字典攻击零成本；
- 单个客户端可以打满所有后端服务线程池，造成全站不可用；
- 浏览器端跨域请求全部失败（无 CORS 配置），前端联调只能走代理；
- `allowCredentials=true` + 通配 `*` 的组合还会导致 cookie 鉴权失效。

**改完什么效果**
- 登录接口具备防爆破能力；
- 单用户异常流量被隔离，不影响其他用户；
- 前端可直连网关；
- 有明确的「谁在打、打了多少」的限流可观测数据。

---

## P0-6　数据库版本化迁移（Flyway）

**改什么**
1. 在 `pan-common` 或独立 `db` 目录建立：
   ```
   db/migration/
     V1__init_mysql.sql        # user_center / file_info / file_object /
                              # recent_access / share_record / operation_log / ai_*
     V2__init_pgvector.sql     # PostgreSQL: vector 扩展 + file_segment 表
   es/file_index.json          # ES mapping（通过脚本下发）
   ```
2. `user-service`（数据归属方）引入 `flyway-core` + `flyway-mysql`，`spring.flyway.enabled=true`；
3. PG 侧由 `ai-service` 或独立迁移 job 执行；
4. ES mapping 用 `scripts/es-init.sh` 幂等建索引。

**为什么改**
全仓 **0 个 `.sql` 文件、0 个 Flyway/Liquibase 依赖**。当前 schema 完全靠手工建表，后果是：

- 换台机器 / 新同事 / 重建测试环境 → 起不来，必须找「知道表结构的那个人」；
- 字段变更无法 review、无法回滚，线上到底是哪个版本没人知道；
- 12 个服务共享一套 MySQL，没有任何机制防止字段漂移；
- 报错是运行时的 `Unknown column`，而不是启动期的快速失败。

**改完什么效果**
- 空库执行迁移即得到完整 schema，`docker compose up` 就能起来；
- schema 变更走 PR，有 diff、有评审、有回滚点；
- 启动时自动校验版本，schema 不一致立刻失败而不是运行到一半报错；
- 这是所有集成测试的先决条件（Testcontainers 也要靠它建库）。

---

## P0-7　清理多套 CI，收敛到一套

**改什么**
当前并存 **4 套**流水线：
- `.github/workflows/ci.yml`
- `.woodpecker.yml`
- `Jenkinsfile`
- `.workflow/master-pipeline.yml`

选一套（建议 GitHub Actions，因为你已有 secrets 配置），删除其余三套及其配置。

同时删除 `.woodpecker/docker-compose.yml` 里的明文密钥：
```yaml
WOODPECKER_AGENT_SECRET=<REDACTED>   # 明文入库，删
```

**为什么改**
四套流水线的编译命令、部署方式、健康检查逻辑各不相同，且都在演进——改一处忘三处，最终没人能确定「push 之后到底跑了什么」。明文 agent secret 一旦仓库转公开即等同于泄露。

**改完什么效果**
- 唯一事实来源，改 CI 只需改一个文件；
- 无明文密钥在仓库里；
- 排查发布问题时有明确入口。

---

# P1 —— 建门禁

## P1-0　配额账目的一致性缺口

**背景**

配额归属划给 user-service 之后，`reserve` / `release` / `add` 从「一次本地 UPDATE」变成了「一次跨服务同步 RPC」。扣减本身仍是安全的——`UserCenterMapper.reserveSpace` 是一条带 `used_space + size <= total_space` 守卫的原子 UPDATE，行锁保证多实例并发只有一个成功，不会超卖。问题全部出在**调用方的幂等与补偿**。

已修：`deleteSingleFile` 缺 `isDeleted` 守卫，同一个文件删两次会退两次配额（`2a5e9a3`，`RecycleBinIdempotencyTest` 锁住）。以下四条未修。

**① `addUsedSpace` 没有任何守卫** —— 逻辑超卖

`UPDATE user_center SET used_space = used_space + #{size}`，不检查 `total_space`。触发路径：删文件到回收站（退配额）→ 上传新文件占满 → 从回收站恢复旧文件 → `used_space > total_space`。不需要任何故障，正常操作即可复现。

改法：要么加 `WHERE used_space + #{size} <= total_space` 并在 0 行时返回错误让前端提示「空间不足，无法恢复」，要么明确接受这个行为（但得写进产品文档，不能是「谁也不知道」）。

**② 熔断降级静默吞掉补偿动作**

`UserSpaceClientFallbackFactory` 里 `reserveSpace` 返回 `false`（安全侧，正确），但 `releaseSpace` / `addUsedSpace` 只有 `log.warn`。user-service 发版或熔断期间，退还和回补直接丢失，**账永久漂移且无人知晓**。

改法：降级时把补偿动作写进一张 `space_compensation` 表（或 Redis 延迟队列），由 `file-task-service` 定时重放，重放成功再删。日志不算补偿。

**③ `tryMarkSpaceReleased` 的「先标记、失败回滚标记」在超时场景下不幂等**

远程调用**已经成功**但客户端读超时（`readTimeout: 5000`）时，catch 到异常 → `unmarkSpaceReleased` 回滚标记 → 下一轮重试再退一次。标记保护的是「本地重复触发」，保护不了「远端已生效但结果未知」。

改法：`/internal/space/*` 引入幂等键（`requestId` 用 `taskId` / `fileId`），user-service 建 `space_op_log(request_id UNIQUE)` 去重。这是治本方案，能同时覆盖 ② 的重放场景。

**④ 没有对账任务**

`used_space` 与 `SUM(t_file_info.file_size WHERE is_deleted = 0)` 之间没有任何校验。上面三条造成的任何漂移都发现不了，只能等用户投诉「我明明删了文件空间没回来」。

改法：`file-task-service` 加每日对账任务（现成的 `@Scheduled + Redisson tryLock` 框架），差异超阈值告警。成本很低，兜住所有还没想到的漂移。

**顺带记一个不是 bug 但要知道的耦合**

`reserve` 是同步 RPC（connect 2s / read 5s），user-service 重启期间降级返回 `false` → **全站上传失败**。单体时代这是本地 SQL，一个服务的可用性现在绑住了所有写路径。要做发布窗口评估的话，这条得算进去。

---

## P1-1　JaCoCo 覆盖率门禁

**改什么**
`user-service/pom.xml` 里已引入 `jacoco-maven-plugin 0.8.12`，但**没有 `check` goal，也没有任何 `limit` 规则**。补上：

```xml
<execution>
  <id>check</id>
  <goals><goal>check</goal></goals>
  <configuration>
    <rules>
      <rule>
        <element>BUNDLE</element>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.60</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```
逐步推广到全部 12 个模块，基线从低到高（先 40% 再 60%）。

**为什么改**
「引入了 JaCoCo」和「有覆盖率门禁」是两件事。当前覆盖率哪怕是 0 也构建成功——**度量存在但无约束，等于没有**。企业级项目里，覆盖率的价值不在数字本身，而在于「新增代码必须带测试」这条硬约束。

**改完什么效果**
- 覆盖率跌破基线，CI 直接红，PR 无法合并；
- 新代码无测试会立刻被拦住；
- 覆盖率趋势可追踪，防止长期缓慢腐化。

---

## P1-2　补集成测试（Testcontainers）

**改什么**
现状：16 个测试文件、**0 个 `@SpringBootTest`**，全是纯 Mockito 单测。补 5 条关键链路：

| 链路 | 覆盖内容 |
|---|---|
| 上传 | 分片合并、秒传命中、STS/直传、配额预占与释放 |
| 下载 | 单文件流式、`Range` 断点续传、文件夹 ZIP 打包 |
| 分享 | 创建 → 密码校验 → Token 校验 → 下载 → 取消 |
| 权限 | 跨用户访问他人文件 / 文件夹 / 分享 |
| MQ 消费 | ES/PG 索引同步、操作日志落库、DLQ 重投 |

技术：`Testcontainers` 起 MySQL / Redis / RabbitMQ / PostgreSQL，OSS 用 MockServer 或 MinIO。

**为什么改**
Mockito 单测验证的是「分支逻辑是否按我写的走」，它**永远无法发现**：事务边界错、SQL 写错、MQ 序列化不兼容、`@Transactional` 失效、Bean 注入循环。目前「编译通过」是唯一的真实验证——这意味着任何一次重构都可能静默破坏核心链路，靠人工点页面来发现。

**改完什么效果**
- 重构有安全网，敢改代码；
- 事务/序列化/跨服务契约这类「编译期看不出」的问题在 CI 阶段暴露；
- 冒烟测试从「手工点 20 个页面」变成「跑一次 CI」。

---

## P1-3　actuator 全量接入 + 健康检查探针

**改什么**
1. 12 个模块统一引入 `spring-boot-starter-actuator`（目前只有 3 个有，且**没有任何 `management:` 配置，端点了没开**）；
2. 配置：
   ```yaml
   management:
     endpoints.web.exposure.include: health,info,metrics,prometheus
     endpoint.health.show-details: when_authorized
     endpoint.health.probes.enabled: true
   ```
3. `deploy/docker-compose.yml` **当前 0 个 `healthcheck`**，全部补上，并用 `depends_on: condition: service_healthy` 保证启动顺序。

**为什么改**
- 引了 actuator 但没配 `management.endpoints`，默认只暴露 `/actuator/health`，metrics/prometheus 全关——等于引了个空壳；
- compose 里 `restart: always` 只在进程退出时重启，**服务假活（启动成功但 DB 连不上、端口占着但处理不了请求）时完全无法发现**；
- 没有健康检查，`docker compose up` 后不知道服务是否可用，只能 `sleep 30` 赌一把（CI 里现在就是这么干的）。

**改完什么效果**
- 编排层能区分「启动中」和「健康」，依赖的服务真正就绪后才启动下游；
- 假死能被自动重启；
- 为 P1-4 的监控提供数据源。

---

## P1-4　可观测性落地（Prometheus + Grafana + 链路追踪）

**改什么**
1. Prometheus 抓取各服务 `/actuator/prometheus`；
2. Grafana 面板：JVM（堆/GC/线程）、HTTP QPS/延迟/P99、MQ 积压、DB 连接池；
3. Micrometer Tracing + OTLP，traceId 贯穿：网关 → Feign → MQ 消费 → 线程池（`MdcTaskDecorator` 已有，需验证传递链完整）；
4. 新增 `logback-spring.xml`（当前**全仓 0 个日志配置文件**），JSON 格式 + 按天滚动 + 保留 30 天；
5. 告警规则：错误率 > 1%、P99 > 1s、MQ 积压 > 1000、服务 down。

**为什么改**
现在出问题只能 `docker logs` 逐个翻，且日志是 Spring 默认格式、无滚动、无结构化——12 个服务的日志散在 12 个容器里，无法关联。`RequestTraceFilter` 生成了 traceId 但没有任何地方能按它检索。**没有可观测性，等于线上是黑盒**：接口变慢了你不知道是 DB、是 Redis、还是下游服务。

**改完什么效果**
- 一个问题从「猜」变成「看」：一次请求的完整链路、各段耗时一目了然；
- 故障在用户投诉前触发告警；
- 容量规划有数据依据（不是拍脑袋加内存）。

---

## P1-5　Feign 规范化：超时 / 重试 / 熔断 / trace

**改什么**
5 个模块引了 Feign，但**无超时、无重试、无熔断、不走 `lb://`**（CLAUDE.md 里也承认「RPC 规范化后续补齐」）。补：
```yaml
feign:
  client.config.default:
    connectTimeout: 2000
    readTimeout: 5000
    loggerLevel: basic
  circuitbreaker.enabled: true
spring.cloud.openfeign.client.config.default.retryer: ...
```
+ `resilience4j-spring-boot3` 熔断 + 降级 fallback；
+ 服务地址从环境变量改为 `lb://user-service`（走 Nacos 服务发现）；
+ traceId 通过 `RequestInterceptor` 透传。

**为什么改**
当前是「裸 RestClient + 硬编码地址」。任何一次下游抖动（DB 慢、GC 停顿）都会让调用方线程一直阻塞到 TCP 超时（默认可能几十秒），线程池迅速打满，**故障从单个服务扩散到全站**——这是最典型的雪崩模式。没有超时的远程调用在企业评审里是一票否决项。

**改完什么效果**
- 下游慢时上游快速失败，熔断器打开后不再打无效请求；
- 故障被限制在单服务内，不扩散；
- traceId 贯穿超过进程边界，跨服务排查可行；
- 服务扩缩容/迁移无需改配置（`lb://` 由注册中心解析）。

---

## P1-6　CI 部署流程改造（灰度 + 自动回滚）

**改什么**
当前 CI 的部署步骤：
```bash
docker compose down && docker compose build && docker compose up -d
```
改为：
1. **单服务滚动**：`docker compose up -d --no-deps <service>`，一次只动一个服务；
2. **健康检查门禁**：滚动后轮询 `/actuator/health` 直到 UP，超时则 `docker compose rollback`（或保留上一版镜像 tag 回滚）；
3. **健康检查失败要让 CI 红**：现在 `curl ... || echo "WARN: login failed"` 把失败吞掉了，改成 `|| exit 1`；
4. 移除硬编码测试账号 `test/<TEST_PASSWORD>`，改用 secrets；
5. 镜像打 commit SHA tag，不用 `latest`。

**为什么改**
`compose down` 再 `up` 意味着**每次发布全站宕机**（12 个服务一起停，重启期间所有用户不可用，且没有回滚能力）。`|| echo "WARN"` 让健康检查失败时 CI 依然是绿的——等于**没有健康检查**。硬编码密码留在仓库里，且那个 `test` 账号如果是真账号就是凭据泄露。

**改完什么效果**
- 发布期间只有目标服务短暂不可用，其他服务无感；
- 新版本起不来自动回滚，不会把线上留在半死状态；
- CI 绿 = 真的可用，值得信任；
- 可精确知道线上跑的是哪个 commit。

---

# P2 —— 工程规范

## P2-1　API 文档（springdoc-openapi）

**改什么** 12 个模块引入 `springdoc-openapi-starter-webmvc-ui`，网关聚合各服务 OpenAPI 为统一入口 `/swagger-ui.html`。

**为什么改** 23 个 `@RestController` 全部无文档，接口契约只存在于代码里。前端联调靠口口相传或读源码。

**改完什么效果** 接口可在线浏览/调试；契约变更在文档上可见；可作为前后端协作依据。

---

## P2-2　依赖漏洞扫描 + 代码质量门禁

**改什么**
- `OWASP Dependency-Check` 或 Dependabot / Trivy 扫描依赖 CVE；
- SpotBugs / Checkstyle 或 SonarQube 接入 CI；
- 增加 PR 模板、CODEOWNERS、commitlint（约定式提交）。

**为什么改** 当前无任何静态检查，`Log4j` 式的依赖漏洞只能等出事发现；代码风格靠个人自觉，review 成本高。

**改完什么效果** 已知 CVE 在合入前被拦截；review 聚焦逻辑而非格式。

---

## P2-3　Maven Wrapper 修复

**改什么** `.mvn/wrapper/maven-wrapper.properties` 缺失，导致 `mvnw` / `mvnw.cmd` 不可用。执行 `mvn wrapper:wrapper` 补齐。

**为什么改** 现在必须依赖「本机装 Maven 3.9+」，新人/CI 环境版本不一致时构建行为不同。

**改完什么效果** `./mvnw` 开箱即用，构建版本确定。

---

## P2-4　密钥治理

**改什么**
1. `user-service/application.yml` 有 `${NACOS_PASSWORD:nacos}` 默认值兜底，去掉默认值（缺失时启动失败而不是静默用弱密码）；
2. git 历史中的明文凭据（RabbitMQ、Woodpecker）全部轮换；
3. Nacos 敏感配置贴 `cipher-` 前缀，开启配置加密；
4. OSS 使用 STS 临时凭证，最小权限策略（当前 `OSS_ACCESS_KEY_ID` 是长期 AK）。

**为什么改** 长期 AK 泄露 = 整个 OSS bucket 任人读写；历史明文凭据即使删了代码也还在 git 里。

**改完什么效果** 即使仓库泄露也不直接等于云账号泄露；密钥可轮换、可审计。

---

## P2-5　Nacos 配置即代码

**改什么** `nacos-config/` 现在有 9 个 Python 脚本（`import-config.py`、`update-one-config.py`、`mem.py` 等），且**未纳入 git**。改为：
- `nacos-config/DEFAULT_GROUP/*.yaml` 作为唯一事实来源入库；
- 一个幂等的 `sync.sh`（对比远端 diff 后推送，支持 dry-run）；
- 配置变更走 PR + 自动同步。

**为什么改** 配置靠手工在 Nacos 控制台改，改了什么、谁改的、能否回滚全都不可知。`mem.py` 这类临时脚本混在里面也是噪音。

**改完什么效果** 配置有版本、有 diff、可回滚；环境重建一条命令。

---

## P2-6　`@Transactional` 覆盖率与事务边界梳理

**改什么** 全仓仅 8 处 `@Transactional`，而涉及多表写的 Service 远不止 8 个。逐个梳理写操作，明确事务边界；跨服务操作（配额扣减 + 文件写入 + MQ 发送）用**本地消息表**或 **Saga** 方案。

**为什么改** 「扣了配额但文件没写成功」「文件写了但 ES 没同步」这类问题，没有事务/补偿就只能靠人工修数据。

**改完什么效果** 单服务内写操作原子；跨服务最终一致，不会出现半成品数据。

---

# P3 —— 长期演进

## P3-1　MQ 消费幂等

`RabbitMqTopologyConfig` 已配 DLX（这点做得对），但消费端**无幂等去重**：消息重投会重复落库。方案：`operation_log.msg_id` 唯一索引 + 先落库后去重，DLQ 人工/自动重投。

## P3-2　分布式限流与降级

网关 + 服务双层限流，配合 Sentinel 规则可视化（依赖已引但未接）。热点参数限流、系统自适应保护。

## P3-3　容器编排升级

Docker Compose 适合单机。若服务数量或多副本需求增长，迁 K8s（StatefulSet 管中间件、Deployment 管服务、Ingress 替代网关部分职责、HPA 自动扩缩）。

## P3-4　多环境隔离

当前只有一个 `deploy/docker-compose.yml`，无 dev/staging/prod 区分。建立 profile 机制与环境专用 compose override。

---

# 执行顺序建议

```
第 1 天      P0-1  P0-2                     （10 分钟，立即做）
第 1-2 天    P0-3  P0-4  P0-5               （安全，需联调验证）
第 3-5 天    P0-6  P0-7                     （迁移 + CI 收敛）
第 2 周      P1-3  P1-1                     （探针 + 覆盖率门禁）
第 3 周      P1-2  P1-5                     （集成测试 + RPC 规范化）
第 4 周      P1-4  P1-6                     （可观测性 + 灰度部署）
第 5 周起    P2-*
```

**关键依赖**：P0-6（Flyway）必须先于 P1-2（Testcontainers），否则测试环境建不出库。

---

# 附：现状数据速查

| 指标 | 现状 | 企业级参考 |
|---|---|---|
| Java 文件 | 393 | — |
| 测试文件 | 16 | — |
| `@SpringBootTest` | 0 | 核心链路全覆盖 |
| 覆盖率门禁 | 无（JaCoCo 未配 check） | ≥ 60% 行覆盖 |
| `.sql` 迁移文件 | 0 | 全量版本化 |
| `healthcheck`（compose） | 0 | 全服务 |
| actuator 模块 | 3 / 12（且未配 endpoints） | 12 / 12 |
| 日志配置文件 | 0 | 每服务结构化日志 |
| API 文档 | 无 | springdoc |
| CI 流水线 | 4 套并存 | 1 套 |
| `/internal/**` 鉴权 | 无 | 内部 token + 签名 |
| 未跟踪大文件 | 2.0 GB | 0 |
