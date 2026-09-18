package com.ff.mycloud.ai.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import com.ff.mycloud.ai.model.ModelFailures;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;

/** Enforces input+schema+output reservation on every call, including planning and retry. */
final class AgentModelCalls {
    private final AgentProperties config;
    private final TokenCountEstimator tokens;
    private final Runnable checkpoint;

    AgentModelCalls(AgentProperties config, TokenCountEstimator tokens, Runnable checkpoint) {
        this.config = config;
        this.tokens = tokens;
        this.checkpoint = checkpoint;
    }

    boolean fits(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return characters(messages, tools) <= config.getMaxContextChars()
                && estimate(messages, tools) + config.getMaxOutputTokens() <= config.getMaxContextTokens();
    }

    ChatResponse call(AgentRun run, ChatModel model, List<ChatMessage> messages, List<ToolSpecification> tools) {
        if (!fits(messages, tools)) throw new AgentExecutionException("CONTEXT_BUDGET_EXCEEDED");
        for (int attempt = 0; attempt < 2; attempt++) {
            checkpoint.run();
            int input = estimate(messages, tools);
            int remaining = config.getMaxTotalTokens() - run.getPriorTokens() - Math.max(run.totalTokens(), run.getBudgetTokens());
            int output = Math.min(config.getMaxOutputTokens(), remaining - input);
            if (run.getModelCalls() >= config.getMaxModelCalls() || output < 256) throw new AgentExecutionException("MODEL_BUDGET_EXCEEDED");
            run.setModelCalls(run.getModelCalls() + 1);
            run.setBudgetTokens(run.getBudgetTokens() + input + output);
            checkpoint.run();
            ChatResponse response;
            try {
                response = model.chat(ChatRequest.builder().messages(messages).toolSpecifications(tools).maxOutputTokens(output).build());
            } catch (RuntimeException error) {
                run.setUsageComplete(false);
                if (attempt == 0 && ModelFailures.retryable(error)) continue; // No tool invocation is retried here.
                throw new AgentExecutionException(ModelFailures.code(error));
            }
            if (response == null || response.aiMessage() == null) throw new AgentExecutionException("EMPTY_MODEL_RESPONSE");
            var usage = response.tokenUsage();
            if (usage == null || usage.inputTokenCount() == null || usage.outputTokenCount() == null) {
                run.setUsageComplete(false); // Keep the full reservation when the provider omits usage.
            } else {
                run.setInputTokens(run.getInputTokens() + usage.inputTokenCount());
                run.setOutputTokens(run.getOutputTokens() + usage.outputTokenCount());
                int consumed = Math.max(input + usage.outputTokenCount(), usage.inputTokenCount() + usage.outputTokenCount());
                run.setBudgetTokens(run.getBudgetTokens() - input - output + consumed);
            }
            checkpoint.run();
            if (response.finishReason() == FinishReason.LENGTH) throw new AgentExecutionException("OUTPUT_TRUNCATED");
            if (response.finishReason() == FinishReason.CONTENT_FILTER) throw new AgentExecutionException("MODEL_RESPONSE_FILTERED");
            return response;
        }
        throw new AgentExecutionException("MODEL_TEMPORARILY_UNAVAILABLE");
    }

    private int estimate(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return tokens.estimateTokenCountInMessages(messages) + tokens.estimateTokenCountInText(tools.toString()) + 128;
    }

    private int characters(List<ChatMessage> messages, List<ToolSpecification> tools) {
        return messages.stream().mapToInt(m -> m.toString().length()).sum() + tools.toString().length();
    }

}
