package com.ff.mycloud.ai.model;

import com.ff.mycloud.ai.memory.ChatMemoryProperties;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.TokenUsage;

import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

/**
 * Budgets every model round; retry is allowed only before any text has been emitted.
 */
public class BudgetedStreamingChatModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    private final TokenCountEstimator tokens;
    private final ChatMemoryProperties config;
    private final Runnable active;
    private final Consumer<TokenUsage> usage;
    private final AtomicInteger calls = new AtomicInteger();

    public BudgetedStreamingChatModel(StreamingChatModel delegate, TokenCountEstimator tokens,
                                      ChatMemoryProperties config, Runnable active, Consumer<TokenUsage> usage) {
        this.delegate = delegate;
        this.tokens = tokens;
        this.config = config;
        this.active = active;
        this.usage = usage;
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        attempt(request, handler, false);
    }

    private void attempt(ChatRequest request, StreamingChatResponseHandler handler, boolean retried) {
        try {
            active.run();
            int estimated = tokens.estimateTokenCountInMessages(request.messages())
                    + tokens.estimateTokenCountInText(String.valueOf(request.toolSpecifications())) + config.getMaxOutputTokens() + 512;
            if (calls.incrementAndGet() > 4 || estimated > config.getContextTokenBudget())
                throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "本轮上下文或工具调用达到上限，请缩小问题范围；历史记录已保留");
            var parameters = request.parameters().overrideWith(dev.langchain4j.model.chat.request.ChatRequestParameters.builder()
                    .maxOutputTokens(config.getMaxOutputTokens()).build());
            var responseHandler = new StreamingChatResponseHandler() {
                private final AtomicBoolean emitted = new AtomicBoolean();
                private final AtomicBoolean terminal = new AtomicBoolean();

                @Override
                public void onPartialResponse(String text) {
                    if (terminal.get()) return;
                    emitted.set(true);
                    try {
                        active.run();
                        handler.onPartialResponse(text);
                    } catch (Exception error) {
                        onError(error);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    if (!terminal.compareAndSet(false, true)) return;
                    try {
                        usage.accept(response.tokenUsage());
                        active.run();
                        handler.onCompleteResponse(response);
                    } catch (Exception error) {
                        handler.onError(error);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (!terminal.compareAndSet(false, true)) return;
                    if (!retried && !emitted.get() && ModelFailures.retryable(error)) attempt(request, handler, true);
                    else handler.onError(error);
                }
            };
            try {
                delegate.chat(request.toBuilder().parameters(parameters).build(), responseHandler);
            } catch (Exception error) {
                responseHandler.onError(error);
            }
        } catch (Exception error) {
            handler.onError(error);
        }
    }
}
