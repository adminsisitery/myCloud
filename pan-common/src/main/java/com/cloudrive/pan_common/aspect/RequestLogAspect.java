package com.cloudrive.pan_common.aspect;

import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 覆盖普通 API 和 AI API，不序列化请求参数、响应数据或原始 URL。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogAspect {
//    @Around("execution(public * com.ff.mycloud.Controller..*(..)) || "
//            + "(execution(public * com.ff.mycloud.ai..*(..)) && @within(org.springframework.web.bind.annotation.RestController))")
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequest(ProceedingJoinPoint point) throws Throwable {
        long start = System.nanoTime();
        String operation = point.getSignature().toShortString();
        log.debug("请求开始: operation={}", operation);
        try {
            Object response = point.proceed();
            Object body = response instanceof ResponseEntity<?> entity ? entity.getBody() : response;
            Integer code = body instanceof Result<?> result ? result.getCode() : null;
            int status = response instanceof ResponseEntity<?> entity ? entity.getStatusCode().value() : servletStatus();
            boolean failed = status >= 400 || (code != null
                    && !Objects.equals(code, CommonConstants.StatusCode.SUCCESS));
            boolean async = body instanceof ResponseBodyEmitter || body instanceof StreamingResponseBody;
            String phase = async ? "async-started" : "completed";
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String format = "请求返回: operation={}, phase={}, status={}, code={}, elapsedMs={}";
            if (failed) log.warn(format, operation, phase, status, code, elapsed);
            else if (isReadOrProgress(operation)) log.debug(format, operation, phase, status, code, elapsed);
            else log.info(format, operation, phase, status, code, elapsed);
            return response;
        } catch (Throwable e) {
            // 全局异常处理器负责记录堆栈，这里只补充接口名和耗时。
            log.warn("请求抛出异常: operation={}, exception={}, elapsedMs={}", operation,
                    e.getClass().getSimpleName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            throw e;
        }
    }

    private int servletStatus() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getResponse() != null) {
            return attributes.getResponse().getStatus();
        }
        return 200;
    }

    private boolean isReadOrProgress(String operation) {
        if (operation.startsWith("BigFileController.upload(")) return true;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String method = attributes.getRequest().getMethod();
            return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        }
        return false;
    }
}
