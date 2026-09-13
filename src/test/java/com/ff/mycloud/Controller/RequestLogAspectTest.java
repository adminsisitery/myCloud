package com.ff.mycloud.Controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ff.mycloud.common.aspect.RequestLogAspect;
import com.ff.mycloud.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;

class RequestLogAspectTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previous;
    private LoggingTestController controller;

    @BeforeEach
    void setup() {
        previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
        AspectJProxyFactory factory = new AspectJProxyFactory(new LoggingTestController());
        factory.addAspect(new RequestLogAspect());
        controller = factory.getProxy();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test?token=secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        logger.detachAppender(appender);
        logger.setLevel(previous);
        appender.stop();
    }

    @Test
    void resultErrorWithHttp200IsWarnAndPayloadIsNeverLogged() {
        Object result = controller.failed("secret-password");
        assertInstanceOf(Result.class, result);
        ILoggingEvent last = appender.list.getLast();
        assertEquals(Level.WARN, last.getLevel());
        assertTrue(last.getFormattedMessage().contains("code=500"));
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("secret")));
    }

    @Test
    void getRequestsUseDebugAndResponseEntityErrorsUseWarn() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/test")));
        controller.success();
        assertEquals(Level.DEBUG, appender.list.getLast().getLevel());
        controller.denied();
        assertEquals(Level.WARN, appender.list.getLast().getLevel());
        assertTrue(appender.list.getLast().getFormattedMessage().contains("status=403"));
    }

    @Test
    void streamingResponseIsOnlyMarkedStarted() {
        assertInstanceOf(SseEmitter.class, controller.stream());
        assertTrue(appender.list.getLast().getFormattedMessage().contains("phase=async-started"));
    }

    @Test
    void exceptionIsRethrownUnchangedWithoutDuplicateStack() {
        RuntimeException failure = new IllegalStateException("secret");
        assertSame(failure, assertThrows(RuntimeException.class, () -> controller.fail(failure)));
        assertNull(appender.list.getLast().getThrowableProxy());
        assertFalse(appender.list.getLast().getFormattedMessage().contains("secret"));
    }

    public static class LoggingTestController {
        public Result<String> failed(String password) { return Result.error("secret-response"); }
        public Result<String> success() { return Result.success("secret-token"); }
        public ResponseEntity<?> denied() { return ResponseEntity.status(403).build(); }
        public SseEmitter stream() { return new SseEmitter(); }
        public void fail(RuntimeException failure) { throw failure; }
    }
}
