package com.ff.mycloud.common.logging;

import com.ff.mycloud.common.aspect.BackgroundLogAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundLogAspectTest {
    @AfterEach
    void cleanup() { MDC.clear(); }

    @Test
    void scheduledAndMqInvocationsHaveIndependentTracesAndRestoreCaller() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Worker());
        factory.addAspect(new BackgroundLogAspect());
        Worker worker = factory.getProxy();
        MDC.put("traceId", "caller");
        String first = worker.scheduled();
        assertTrue(first.matches("[a-f0-9]{32}"));
        assertNotEquals(first, worker.message());
        assertEquals("caller", MDC.get("traceId"));
        IllegalStateException failure = new IllegalStateException("expected");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> worker.fail(failure)));
        assertEquals("caller", MDC.get("traceId"));
    }

    public static class Worker {
        @Scheduled(fixedDelay = 1000)
        public String scheduled() { return MDC.get("traceId"); }
        @RabbitListener(queues = "test")
        public String message() { return MDC.get("traceId"); }
        @RabbitListener(queues = "test")
        public void fail(IllegalStateException failure) { throw failure; }
    }
}
