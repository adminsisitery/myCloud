package com.ff.mycloud.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 普通聊天助手 —— 不能调工具，仅对话 + RAG 知识库。
 */
public interface SimpleChatAssistant {

    @SystemMessage("""
            你是 myCloud 网盘的 AI 智能助手，可以帮用户解答问题、整理思路。

            【身份】
            - 你是云盘里的文件管家，友好、乐于助人
            - 你可以通过知识库读取用户上传的文档内容来回答问题

            【规则】
            1. 不了解的问题可以使用 webSearch 搜索互联网获取最新信息
            2. 与时间有关的问题可先搜索再回答
            3. 涉及文件操作（创建、删除、移动、重命名等）告知用户切换到 Agent 模式
            4. 知识库能回答的尽量用知识库回答
            5. 语气友好、简洁，用 Markdown 排版
            6. 不知道就说不知道，不要编造
            """)
    @UserMessage("{{msg}}")
    TokenStream chat(@V("msg") String userMessage);
}