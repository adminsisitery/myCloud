package com.cloudrive.aiService.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prompt 模板服务 —— 按场景名返回 System Prompt。
 * <p>模板在 application.yml 的 {@code ai.prompts} 下定义，
 * 支持多行 YAML 格式。运行时可通过此服务按场景名获取。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 *   .systemMessageProvider(memoryId -> promptService.get("agent"))
 * }</pre>
 * </p>
 *
 * @author 飞飞
 * @date 2026/8/2
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final AIProperties properties;

    public PromptService(AIProperties properties) {
        this.properties = properties;
    }

    /** 按场景名获取 prompt 模板 */
    public String get(String scene) {
        AIProperties.Prompts prompts = properties.getPrompts();
        if (prompts == null) {
            log.warn("ai.prompts 未配置，返回空模板");
            return "";
        }
        Map<String, String> map = prompts.toMap();
        String template = map.get(scene);
        if (template == null || template.isBlank()) {
            log.warn("Prompt 模板 '{}' 不存在，返回空", scene);
            return "";
        }
        return template.trim();
    }

    /** 获取 Agent 模式 System Prompt */
    public String getAgentPrompt() {
        String p = get("agent");
        return p.isEmpty() ? get("chat") : p; // agent 无配置时降级到 chat
    }

    /** 获取普通聊天模式 System Prompt */
    public String getChatPrompt() {
        return get("chat");
    }

    /** 获取 Agent 规划阶段 Prompt */
    public String getAgentPlanningPrompt() {
        String p = get("agent-planning");
        return p.isEmpty() ? getDefaultPlanningPrompt() : p;
    }

    // ==================== 降级默认值 ====================

    /** Agent 规划阶段 Prompt 的硬编码降级 */
    private static String getDefaultPlanningPrompt() {
        return """
                你是一个任务规划器。根据用户的请求，将其分解为具体的执行步骤。

                【可用工具】
                文件浏览: listAllFiles, listDirectory(parentId), findFile(keyWord)
                文件操作: createFolder(parentId, folderName), renameFile(currentName, newName),
                         deleteFile(fileName), moveFile(fileName, targetFolderName), copyFile(fileName)
                收藏回收: starFile(fileName), unstarFile(fileName), listStarredFiles,
                         getTrashList, restoreFile(fileName), cleanTrash
                其他: getStorageInfo, getRecentFiles, getNowDate, webSearch(query)

                【用户请求】
                %s

                【输出格式】严格输出以下 JSON（不要输出其他内容）:
                {
                  "goal": "用户目标的简洁概括",
                  "steps": ["步骤1描述", "步骤2描述", ...]
                }

                【规则】
                1. 每个步骤必须是自然语言，能被AI助手独立理解执行
                2. 涉及文件操作要具体描述（文件名、位置等）
                3. 步骤数 2-6 个，简单请求 1 个即可
                4. 文件操作前先浏览确认
                """;
    }
}
