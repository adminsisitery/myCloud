package com.ff.mycloud.common.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LogbackConfigurationTest {
    @TempDir Path directory;

    @Test
    void productionConfigWritesTraceAndSeparatesErrorFile() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("MYCLOUD_LOG_PATH", directory.toString().replace('\\', '/'));
        // 独立上下文需要与 SLF4J 共用 MDC adapter。
        context.setMDCAdapter(MDC.getMDCAdapter());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try (TraceContext ignored = TraceContext.open(java.util.Map.of("traceId", "config-test"))) {
            configurator.doConfigure(Path.of("src/main/resources/logback.xml").toFile());
            var logger = context.getLogger("com.ff.mycloud.config-test");
            logger.info("info-event");
            logger.warn("warn-event");
            logger.error("error-event");
            assertTrue(context.getStatusManager().getCopyOfStatusList().stream()
                    .noneMatch(status -> status.getLevel() == Status.ERROR));
        } finally {
            context.stop();
        }
        String business = Files.readString(directory.resolve("business.log"));
        String errors = Files.readString(directory.resolve("business-error.log"));
        assertTrue(business.contains("traceId=config-test"));
        assertTrue(business.contains("info-event"));
        assertTrue(business.contains("warn-event"));
        assertTrue(business.contains("error-event"));
        assertTrue(errors.contains("traceId=config-test"));
        assertTrue(errors.contains("error-event"));
        assertFalse(errors.contains("info-event"));
        assertFalse(errors.contains("warn-event"));
    }
}
