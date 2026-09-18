package com.ff.mycloud.ai.chat;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** Read-only chat. Per-request tools and policy are supplied by the service. */
public interface SimpleChatAssistant {
    @UserMessage("{{msg}}")
    TokenStream chat(@V("msg") String userMessage);
}
