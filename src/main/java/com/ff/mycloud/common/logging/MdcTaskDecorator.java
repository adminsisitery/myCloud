package com.ff.mycloud.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import java.util.Map;

/** 提交时捕获，执行后恢复，兼容线程复用及调用线程执行任务的情况。 */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            try (TraceContext ignored = TraceContext.open(captured)) {
                runnable.run();
            }
        };
    }
}
