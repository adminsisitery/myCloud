package com.ff.mycloud.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ChatAssistant {

    @SystemMessage("""
            你是 myCloud 网盘的 AI 智能助手。

            【规则】
            1. 优先使用网盘工具回答文件相关问题。
            2. 在问时间有关的问题的时候先要获取当前时间
            3. 如果用户问的是网盘无法回答的问题（如最新新闻、技术知识等），可以使用 webSearch 搜索互联网。
            4. 工具返回的内容已经是标准 Markdown，直接原样输出，不要修改格式。
            5. 只基于工具返回的数据回答，禁止编造任何文件信息。
            """)
    @UserMessage("{{msg}}")
    TokenStream chat(@V("msg") String userMessage);
}
