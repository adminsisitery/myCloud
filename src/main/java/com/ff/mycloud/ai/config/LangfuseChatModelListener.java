package com.ff.mycloud.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 将 LangChain4j 的 LLM 调用上报到 Langfuse Cloud。
 * <p>
 * 每个 HTTP 请求内共享同一个 Trace，多次 LLM 调用作为多个 Generation。
 * 非 Web 线程（如后台任务）每次调用独立 Trace。
 */
public class LangfuseChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseChatModelListener.class);
    private static final String ATTR_TRACE_ID = "langfuse.traceId";

    private final String baseUrl;
    private final String authHeader;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "langfuse-ingestion");
        t.setDaemon(true);
        return t;
    });

    public LangfuseChatModelListener(LangfuseProperties props, ObjectMapper objectMapper) {
        this.baseUrl = props.getBaseUrl();
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (props.getPublicKey() + ":" + props.getSecretKey()).getBytes(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put("langfuse.startTime", Instant.now());
        ctx.attributes().put("langfuse.model", modelName(ctx));
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            Instant startTime = (Instant) ctx.attributes().get("langfuse.startTime");
            String model = (String) ctx.attributes().get("langfuse.model");
            Instant endTime = Instant.now();

            String traceId = getOrCreateTraceId();
            String genId = UUID.randomUUID().toString();

            var usage = ctx.chatResponse().tokenUsage();

            String inputJson = safeSerialize(ctx.chatRequest().messages());
            String outputJson = safeSerialize(ctx.chatResponse().aiMessage());

            Map<String, Object> batch = new LinkedHashMap<>();
            batch.put("batch", List.of(
                    traceCreateEvent(traceId),
                    generationCreateEvent(genId, traceId, model, inputJson, outputJson,
                            startTime, endTime, usage.inputTokenCount(), usage.outputTokenCount())
            ));

            postToLangfuse(batch);

        } catch (Exception e) {
            log.warn("Langfuse onResponse failed: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        try {
            String model = (String) ctx.attributes().get("langfuse.model");
            String traceId = getOrCreateTraceId();
            String genId = UUID.randomUUID().toString();

            String inputJson = safeSerialize(ctx.chatRequest().messages());

            Map<String, Object> batch = new LinkedHashMap<>();
            batch.put("batch", List.of(
                    traceCreateEvent(traceId),
                    errorGenerationEvent(genId, traceId, model, inputJson, ctx.error().getMessage())
            ));

            postToLangfuse(batch);

        } catch (Exception e) {
            log.warn("Langfuse onError failed: {}", e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /** 获取或创建当前请求的 Trace ID */
    private String getOrCreateTraceId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String traceId = (String) attrs.getAttribute(ATTR_TRACE_ID, RequestAttributes.SCOPE_REQUEST);
            if (traceId == null) {
                traceId = UUID.randomUUID().toString();
                attrs.setAttribute(ATTR_TRACE_ID, traceId, RequestAttributes.SCOPE_REQUEST);
            }
            return traceId;
        }
        return UUID.randomUUID().toString();
    }

    private String modelName(ChatModelRequestContext ctx) {
        String model = ctx.chatRequest().modelName();
        return model != null ? model : "unknown";
    }

    // --- 事件构造 ---

    private Map<String, Object> traceCreateEvent(String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", traceId);
        body.put("name", "chat-completion");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", "trace-create");
        event.put("timestamp", Instant.now().toString());
        event.put("body", body);
        return event;
    }

    private Map<String, Object> generationCreateEvent(String genId, String traceId, String model,
                                                        String input, String output,
                                                        Instant startTime, Instant endTime,
                                                        int inputTokens, int outputTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", genId);
        body.put("traceId", traceId);
        body.put("name", model);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());
        body.put("model", model);
        body.put("input", input);
        body.put("output", output);

        Map<String, Integer> usageMap = new LinkedHashMap<>();
        usageMap.put("input", inputTokens);
        usageMap.put("output", outputTokens);
        usageMap.put("total", inputTokens + outputTokens);
        body.put("usage", usageMap);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", "generation-create");
        event.put("timestamp", endTime.toString());
        event.put("body", body);
        return event;
    }

    private Map<String, Object> errorGenerationEvent(String genId, String traceId, String model,
                                                      String input, String errorMsg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", genId);
        body.put("traceId", traceId);
        body.put("name", model);
        body.put("startTime", Instant.now().toString());
        body.put("endTime", Instant.now().toString());
        body.put("model", model);
        body.put("input", input);
        body.put("level", "ERROR");
        body.put("statusMessage", errorMsg);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("type", "generation-create");
        event.put("timestamp", Instant.now().toString());
        event.put("body", body);
        return event;
    }

    // --- HTTP 发送 ---

    private void postToLangfuse(Object body) {
        executor.submit(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", authHeader);

                String json = objectMapper.writeValueAsString(body);
                HttpEntity<String> entity = new HttpEntity<>(json, headers);

                String url = baseUrl + "/api/public/ingestion";
                restTemplate.postForEntity(url, entity, String.class);
            } catch (JsonProcessingException e) {
                log.warn("Langfuse JSON serialization failed: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Langfuse ingestion request failed (ignored): {}", e.getMessage());
            }
        });
    }

    private String safeSerialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "\"serialization error\"";
        }
    }
}
