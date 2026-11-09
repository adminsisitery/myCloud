package com.cloudrive.pan_common.internal;

/**
 * 服务间鉴权使用的请求头名称。
 * <p>
 * 调用方（Feign）与服务端（{@link InternalAuthInterceptor}）共用同一组常量，
 * 避免两边字符串漂移导致「明明配了 token 却 401」。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
public final class InternalAuthHeaders {

    /** 服务间共享密钥。 */
    // myrc: ignore[SEC005] 这是 HTTP 头名不是密钥，值由 INTERNAL_TOKEN 注入
    public static final String TOKEN = "X-Internal-Token";

    /** 调用发生时刻的毫秒时间戳，用于防重放。 */
    public static final String TIMESTAMP = "X-Internal-Timestamp";

    private InternalAuthHeaders() {
    }
}
