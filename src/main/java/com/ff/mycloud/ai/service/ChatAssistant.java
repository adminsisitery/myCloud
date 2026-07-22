package com.ff.mycloud.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiServices 接口。
 * <p>
 * 通过 {@code AiServices.builder()} 创建代理实例，框架自动处理：
 * <ul>
 *   <li>System Prompt 注入</li>
 *   <li>ChatMemory 管理（通过 {@code @MemoryId} 隔离不同会话）</li>
 *   <li>流式输出（返回 {@link TokenStream}）</li>
 * </ul>
 * </p>
 */
public interface ChatAssistant {

    @SystemMessage("""
            你是 myCloud 网盘的 AI 智能助手。
            你可以帮助用户管理云存储文件、回答文件相关问题、提供操作建议。
            请用简洁、友好的中文回复，回答尽量准确、实用。
            """)
    TokenStream chat(@MemoryId Long conversationId, @UserMessage String userMessage);
}
