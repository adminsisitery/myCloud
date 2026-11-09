# Nacos 开启鉴权操作手册

> 适用版本：**Nacos 2.1.0（standalone）**，实测于 `<NACOS_HOST>:8848`
> 编写时间：2026-10-23

---

## 一、动手前：当前状态

实测四个请求（全部不带 token）就能看出鉴权是关的：

| 请求 | 结果 |
| --- | --- |
| `POST /nacos/v1/auth/login`（错密码） | `403 unknown user!` |
| `POST /nacos/v1/auth/login`（nacos/&lt;NACOS_PASSWORD&gt;） | `403 unknown user!` |
| `GET /nacos/v1/cs/configs?dataId=pan-mysql&group=DEFAULT_GROUP` | `200` + 完整配置 |
| `GET /nacos/v1/auth/users` | `200` + 用户表（含 bcrypt 密码哈希） |

**关键认知**：Nacos 未开启鉴权时，`/v1/auth/login` 对任何密码都返回 `unknown user!`，根本不校验。在控制台改 `nacos` 用户密码**不会**打开鉴权开关——开关是 `application.properties` 里的 `nacos.core.auth.enabled`，默认 `false`。

当前 `nacos` 用户哈希：`$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu`（与发行版默认 `nacos/nacos` 一致，开启鉴权后请立即改掉）。

---

## 二、服务端改动（需登服务器操作）

> **部署方式：Docker（`nacos/nacos-server:v2.1.0-slim`）**
> slim 镜像的 `conf/application.properties` 是精简版，且**容器重建时会被 entrypoint 用环境变量重新渲染**。
> 用 `docker exec` 改容器内文件，重建即失效——必须改 compose 的 `environment`，然后
> `docker compose up -d --force-recreate nacos`（**不是 restart**）。

### 方案 A：Docker（当前环境）

```yaml
  nacos:
    image: nacos/nacos-server:v2.1.0-slim
    container_name: nacos
    environment:
      MODE: standalone
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_SYSTEM_TYPE: nacos
      NACOS_AUTH_IDENTITY_KEY: mycloud
      NACOS_AUTH_IDENTITY_VALUE: mycloud-2026
      # 2.1.0 镜像里 NACOS_AUTH_TOKEN 映射到 nacos.core.auth.default.token.secret.key
      # Base64，解码后需 >= 32 字节；这串是纯字母数字，省去 YAML 引号麻烦
      NACOS_AUTH_TOKEN: "<BASE64_32BYTES_SECRET>"
    ports:
      - "8848:8848"
      - "9848:9848"
```

### 方案 B：裸机

编辑 `$NACOS_HOME/conf/application.properties`，找到或追加：

```properties
### 鉴权总开关
nacos.core.auth.enabled=true
nacos.core.auth.system.type=nacos
nacos.core.auth.caching.enabled=true

### 服务端身份识别，集群模式下每个节点必须唯一；单机也建议配置
nacos.core.auth.server.identity.key=mycloud
nacos.core.auth.server.identity.value=mycloud-2026

### Token 密钥（2.1.0 的属性名是 default.token.secret.key）
### 值必须是 Base64，解码后 >= 32 字节，否则鉴权会失败
nacos.core.auth.default.token.secret.key=<BASE64_32BYTES_SECRET>
nacos.core.auth.default.token.expire.seconds=18000
```

> **版本差异**：Nacos 2.2.0+ 把 token 密钥属性改名为
> `nacos.core.auth.plugin.nacos.token.secret.key`，且**强制要求**显式配置（不配启动直接失败）。
> 如果哪天升级，记得同步改属性名。

重启：

```bash
sh bin/shutdown.sh
sh bin/startup.sh -m standalone
```

### 确认真实生效的配置（Docker 尤其重要）

改完重启后，先看容器里**实际生效**的那份配置，而不是你改的那份：

```bash
docker exec nacos sh -c "grep -iE 'nacos.core.auth' /home/nacos/conf/application.properties"
docker logs nacos 2>&1 | grep -iE "auth" | head -30
```

日志里若出现 `IAuthManager` 初始化异常，说明鉴权管理器没起来，`AuthFilter` 会 fail-open
——表现为「login 能用但所有接口都不用 token」，正是本文档开头描述的现象。

---

## 三、客户端改动（已完成，无需再动）

11 个启动引导 yml 已加上凭据占位：

```yaml
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:<NACOS_HOST>:8848}
      # Nacos 开启鉴权后必须提供；密码不落库，走环境变量 NACOS_PASSWORD
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:}
```

涉及：`user-service`、`ai-service`、`file-upload-service`、`file-download-service`、`file-manage-service`、
`file-search-service`、`file-share-service`、`file-preview-service`、`file-async-service`、
`file-task-service`、`pan-gateway`。

`spring.cloud.nacos.username/password` 会被 `config` 与 `discovery` 两个子配置继承，不必分别写。

---

## 四、环境变量（开鉴权后必须配，否则服务全部 403）

```cmd
setx NACOS_USERNAME nacos
setx NACOS_PASSWORD <你的新密码>
```

或者写进 IDEA 的运行配置。**注意 `setx` 设置的环境变量，已打开的 IDEA 必须重启才能读到。**

---

## 五、验证清单

1. 不带 token 读配置应当失败：

   ```bash
   curl "http://<NACOS_HOST>:8848/nacos/v1/cs/configs?dataId=pan-mysql&group=DEFAULT_GROUP"
   # 期望 403，而不是返回配置内容
   ```

2. 用正确密码登录应当拿到 token：

   ```bash
   curl -X POST "http://<NACOS_HOST>:8848/nacos/v1/auth/login" \
        -d "username=nacos&password=你的密码"
   # 期望 {"accessToken":"...","tokenTtl":18000,"globalAdmin":true}
   ```

3. 服务能正常注册与拉配置：启动 `user-service`，日志里应出现
   `nacos registry, DEFAULT_GROUP user-service ... register finished`。

---

## 六、回滚

把 `nacos.core.auth.enabled` 改回 `false`，重启 Nacos 即可。客户端的 `username/password`
在鉴权关闭时会被忽略，不影响启动，因此**不需要回退 yml**。

---

## 七、遗留事项

- `pan-mysql` / `pan-redis` 的密码默认值目前是 `<DB_PASSWORD>`，`pan-ai` 的 `pgvector.password` 默认 `<PG_PASSWORD>`。
  开鉴权后配置不再公网可读，可以保留；若要求「配置中心零密钥」，改回空值并改用环境变量。
  （`pgvector.password` 上有 `@NotBlank`，不能留空，需保留占位值或去掉该注解。）
- `pan-mybitis` 这个 dataId 在 Nacos 上不存在（404），7 个服务仍在 import 它。
  内容只有 `mapper-locations` 与 `map-underscore-to-camel-case`，恰好与 MyBatis-Plus 默认值相同，
  所以一直静默无效。建议补建该配置，或从 import 列表中删掉。
