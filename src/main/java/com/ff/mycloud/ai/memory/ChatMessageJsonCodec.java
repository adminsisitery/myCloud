package com.ff.mycloud.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatMessage 列表 ↔ JSON 双向序列化。
 * <p>
 * 参照 LC4j 官方 PersistentChatMemoryStore 示例，将完整消息列表
 *（含工具调用/结果）序列化为 JSON 存储，保证工具循环中模型能看到
 * 自己的工具调用历史。
 */
final class ChatMessageJsonCodec {

    private static final ObjectMapper mapper = new ObjectMapper();

    private ChatMessageJsonCodec() {}

    // ==================== 序列化 ====================

    static String toJson(List<ChatMessage> messages) {
        ArrayNode arr = mapper.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode node = mapper.createObjectNode();
            if (msg instanceof UserMessage um) {
                node.put("@type", "USER");
                node.put("text", textOfUser(um));
            } else if (msg instanceof AiMessage aim) {
                node.put("@type", "AI");
                String text = aim.text();
                if (text != null && !text.isBlank()) {
                    node.put("text", text);
                }
                if (aim.hasToolExecutionRequests()) {
                    ArrayNode tcArr = mapper.createArrayNode();
                    for (ToolExecutionRequest tr : aim.toolExecutionRequests()) {
                        ObjectNode tn = mapper.createObjectNode();
                        tn.put("id", tr.id());
                        tn.put("name", tr.name());
                        tn.put("arguments", tr.arguments());
                        tcArr.add(tn);
                    }
                    node.set("toolCalls", tcArr);
                }
            } else if (msg instanceof ToolExecutionResultMessage trm) {
                node.put("@type", "TOOL_RESULT");
                node.put("id", trm.id());
                node.put("toolName", trm.toolName());
                node.put("text", trm.text());
            } else if (msg instanceof SystemMessage sm) {
                node.put("@type", "SYSTEM");
                node.put("text", sm.text());
            }
            arr.add(node);
        }
        return arr.toString();
    }

    // ==================== 反序列化 ====================

    static List<ChatMessage> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<ChatMessage> result = new ArrayList<>();
        try {
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) return result;
            for (JsonNode node : arr) {
                String type = node.has("@type") ? node.get("@type").asText() : "";
                result.add(parseMessage(type, node));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize chat memory", e);
        }
        return result;
    }

    private static ChatMessage parseMessage(String type, JsonNode node) {
        return switch (type) {
            case "USER" -> {
                String text = node.has("text") ? node.get("text").asText() : "";
                yield new UserMessage(text);
            }
            case "AI" -> {
                String text = node.has("text") ? node.get("text").asText() : null;
                List<ToolExecutionRequest> toolCalls = null;
                if (node.has("toolCalls") && node.get("toolCalls").isArray()) {
                    toolCalls = new ArrayList<>();
                    for (JsonNode tc : node.get("toolCalls")) {
                        toolCalls.add(ToolExecutionRequest.builder()
                                .id(tc.get("id").asText())
                                .name(tc.get("name").asText())
                                .arguments(tc.get("arguments").asText())
                                .build());
                    }
                }
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    yield new AiMessage(text, toolCalls);
                } else {
                    yield new AiMessage(text != null ? text : "");
                }
            }
            case "TOOL_RESULT" -> {
                String id = node.get("id").asText();
                String toolName = node.get("toolName").asText();
                String text = node.get("text").asText();
                yield new ToolExecutionResultMessage(id, toolName, text);
            }
            case "SYSTEM" -> {
                String text = node.has("text") ? node.get("text").asText() : "";
                yield new SystemMessage(text);
            }
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }

    private static String textOfUser(UserMessage msg) {
        try {
            String t = msg.singleText();
            if (t != null && !t.isBlank()) return stripRagAugmentation(t);
        } catch (Exception ignored) {}
        try {
            for (dev.langchain4j.data.message.Content c : msg.contents()) {
                if (c instanceof dev.langchain4j.data.message.TextContent tc) {
                    String t = tc.text();
                    if (t != null && !t.isBlank()) return stripRagAugmentation(t);
                }
            }
        } catch (Exception ignored) {}
        return msg.toString();
    }

    private static String stripRagAugmentation(String text) {
        if (text == null || text.length() <= 200) return text;
        int idx = text.indexOf("\n\nAnswer using the following information:");
        if (idx > 0) return text.substring(0, idx).trim();
        idx = text.indexOf("\n\nHere are");
        if (idx > 0) return text.substring(0, idx).trim();
        idx = text.indexOf("\n\nBased on the following");
        if (idx > 0) return text.substring(0, idx).trim();
        return text;
    }
}
