package com.cloudrive.aiService.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 联网搜索工具，使用 Tavily Search API。
 * <p>
 * per-request 实例，带调用次数限制防止 DeepSeek 模型死循环。
 */
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_CALLS = 2;
    private static final int MAX_RESULTS = 5;

    private final WebSearchEngine engine;
    private final AtomicInteger count = new AtomicInteger(0);

    public WebSearchTool(WebSearchEngine engine) {
        this.engine = engine;
    }

    @Tool("搜索互联网获取最新信息，返回搜索结果列表")
    public String webSearch(String query) {
        if (query==null || query.isBlank() || query.length()>1000) return "ERROR: 搜索词应为1到1000字符";
        if (count.incrementAndGet() > MAX_CALLS) {
            log.warn("[Tool] webSearch 已达上限({})，拒绝执行", MAX_CALLS);
            return "搜索次数已达上限，请直接基于已有信息回答用户。";
        }

        log.debug("[Tool] webSearch queryLength={}", query == null ? 0 : query.length());

        try {
            List<? extends WebSearchOrganicResult> results = engine.search(query).results();
            if (results == null || results.isEmpty()) {
                return "未找到与 **" + query + "** 相关的搜索结果。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索 **").append(query).append("** — ").append(results.size()).append("个结果\n\n");

            int n = Math.min(results.size(), MAX_RESULTS);
            for (int i = 0; i < n; i++) {
                WebSearchOrganicResult r = results.get(i);
                sb.append(i + 1).append(". **").append(r.title()).append("**\n");
                sb.append("   ").append(r.snippet()).append("\n");
                sb.append("   ").append(r.url()).append("\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[Tool] webSearch failed", e);
            return "ERROR: 搜索服务暂时不可用";
        }
    }
}
