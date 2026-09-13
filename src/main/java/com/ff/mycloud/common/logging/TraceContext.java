package com.ff.mycloud.common.logging;

import org.slf4j.MDC;
import java.util.Map;
import java.util.UUID;

/** 仅传递日志上下文；不传递登录态，也不信任客户端提交的 traceId。 */
public final class TraceContext implements AutoCloseable {
    private final Map<String, String> previous;

    private TraceContext(Map<String, String> context) {
        previous = MDC.getCopyOfContextMap();
        if (context == null) MDC.clear();
        else MDC.setContextMap(context);
    }

    public static TraceContext open(Map<String, String> context) {
        return new TraceContext(context);
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void close() {
        if (previous == null) MDC.clear();
        else MDC.setContextMap(previous);
    }
}
