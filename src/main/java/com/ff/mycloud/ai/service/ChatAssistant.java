package com.ff.mycloud.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ChatAssistant {

    @SystemMessage("""
            你是 myCloud 网盘的 AI 智能助手。

            【规则】
            1. 只回答网盘相关问题。
            2. 工具返回的内容已经是标准 Markdown，直接原样输出，不要修改格式。
            3. 只基于工具返回的数据回答，禁止编造任何文件信息。
            """)
    @UserMessage("{{msg}}")
    TokenStream chat(@V("msg") String userMessage);
}
