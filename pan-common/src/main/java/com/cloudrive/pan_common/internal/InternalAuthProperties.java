package com.cloudrive.pan_common.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间鉴权配置（前缀 {@code pan.internal}）。
 * <p>
 * 所有服务通过共享配置 {@code pan-common} 读取同一份 token，
 * 实际值由环境变量 {@code INTERNAL_TOKEN} 注入，仓库里不落明文。
 * </p>
 * <p>
 * <b>故意不提供代码默认值</b>：漏配就该失败。若给了默认值，公开仓库里的
 * 默认 token 等于把内网接口直接开放给所有人。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
@ConfigurationProperties(prefix = "pan.internal")
public class InternalAuthProperties {

    /** 服务间共享密钥；为空表示未配置，此时所有 /internal/** 请求一律拒绝。 */
    private String token = "";

    /** 允许的时钟偏差，超出即视为重放。 */
    private Duration tolerance = Duration.ofSeconds(60);

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public Duration getTolerance() {
        return tolerance;
    }

    public void setTolerance(Duration tolerance) {
        this.tolerance = tolerance == null ? Duration.ofSeconds(60) : tolerance;
    }

    /** token 是否已配置。 */
    public boolean isConfigured() {
        return !token.isEmpty();
    }
}
