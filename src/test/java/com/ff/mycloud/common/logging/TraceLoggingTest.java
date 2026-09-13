package com.ff.mycloud.common.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TraceLoggingTest {
    @AfterEach
    void clearMdc() { MDC.clear(); }

    @Test
    void generatesServerTraceAndRestoresCallerEvenOnFailure() {
        MDC.put("traceId", "previous");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "untrusted\r\nforged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("expected");
        assertSame(failure, assertThrows(ServletException.class, () ->
                new RequestTraceFilter().doFilter(request, response, (req, res) -> {
                    assertTrue(MDC.get("traceId").matches("[a-f0-9]{32}"));
                    assertEquals(response.getHeader("X-Trace-Id"), MDC.get("traceId"));
                    throw failure;
                })));
        assertEquals("previous", MDC.get("traceId"));
    }

    @Test
    void asyncDispatchReusesTraceAndNextRequestGetsNewTrace() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> first = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> first.set(MDC.get("traceId")));
        assertNull(MDC.get("traceId"));
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, (req, res) -> assertEquals(first.get(), MDC.get("traceId")));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> assertNotEquals(first.get(), MDC.get("traceId")));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void taskCapturesAtSubmissionAndRestoresWorkerAfterException() {
        MDC.put("traceId", "request");
        IllegalStateException failure = new IllegalStateException("expected");
        Runnable task = new MdcTaskDecorator().decorate(() -> {
            assertEquals("request", MDC.get("traceId"));
            MDC.put("temporary", "value");
            throw failure;
        });
        MDC.put("traceId", "worker");
        assertSame(failure, assertThrows(IllegalStateException.class, task::run));
        assertEquals("worker", MDC.get("traceId"));
        assertNull(MDC.get("temporary"));
    }

    @Test
    void taskWithoutContextDoesNotInheritWorkerContext() {
        Runnable task = new MdcTaskDecorator().decorate(() -> assertNull(MDC.get("traceId")));
        MDC.put("traceId", "worker");
        task.run();
        assertEquals("worker", MDC.get("traceId"));
    }
}
