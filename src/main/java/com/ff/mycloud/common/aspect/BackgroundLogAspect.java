package com.ff.mycloud.common.aspect;

import com.ff.mycloud.common.logging.TraceContext;
import com.ff.mycloud.model.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 每次调度、每次消息消费使用独立 traceId；业务 ID 由处理器日志补充。 */
@Slf4j
@Aspect
@Component
public class BackgroundLogAspect {
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || "
            + "@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object logExecution(ProceedingJoinPoint point) throws Throwable {
        try (TraceContext ignored = TraceContext.open(Map.of("traceId", TraceContext.newTraceId()))) {
            String operation = point.getSignature().toShortString();
            String businessId = businessId(point.getArgs());
            long start = System.nanoTime();
            log.debug("后台执行开始: operation={}, businessId={}", operation, businessId);
            try {
                Object result = point.proceed();
                log.debug("后台调用返回: operation={}, elapsedMs={}", operation,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                return result;
            } catch (Throwable e) {
                log.error("后台执行失败: operation={}, businessId={}, elapsedMs={}", operation, businessId,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), e);
                throw e;
            }
        }
    }
    private String businessId(Object[] args) {
        if (args == null || args.length == 0) return "none";
        Object message = args[0];
        Object id = null;
        if (message instanceof OperationLog entry) id = entry.getMsgId();
        else if (message instanceof FileDocument file) id = file.getId();
        else if (message instanceof PgSender file) id = file.getFileId();
        else if (message instanceof UsageMessage usage) id = usage.getUserId();
        else if (message instanceof String taskId) id = taskId;
        if (id == null) return "none";
        String value = id.toString();
        return value.matches("[a-zA-Z0-9_.:-]{1,128}") ? value : "invalid";
    }
}
