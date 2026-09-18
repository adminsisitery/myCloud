package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.*;

/** JSON envelopes keep status and retrieval handles intact even when the body is large. */
final class AgentToolResults {
    private static final ObjectMapper JSON = new ObjectMapper();

    static int argumentLimit(String tool) {
        return switch (tool) {
            case "draftDocument" -> 196608; // Includes worst-case JSON escaping for 30k characters.
            case "appendDocumentDraft" -> 32768;
            default -> 8000;
        };
    }

    static AgentTaskState.Observation save(AgentRun run, ToolExecutionRequest request, String result, int limit) {
        String safeResult = result == null ? "ERROR: 工具无结果" : result;
        int used = run.getTaskState().getObservations().values().stream().mapToInt(o -> o.result().length() + o.arguments().length()).sum();
        String arguments = Objects.toString(request.arguments(), "{}");
        if (used + safeResult.length() + arguments.length() > limit) throw new AgentExecutionException("EVIDENCE_STORAGE_LIMIT");
        String id = "obs_" + UUID.randomUUID();
        var observation = new AgentTaskState.Observation(id, request.name(), arguments, safeResult, !failed(safeResult));
        run.getTaskState().getObservations().put(id, observation);
        return observation;
    }

    static String context(AgentTaskState.Observation observation) {
        var envelope = JSON.createObjectNode();
        envelope.put("observationId", observation.id());
        envelope.put("success", observation.success());
        envelope.put("tool", observation.tool());
        if (observation.result().length() <= 10000) {
            envelope.set("data", parse(observation.result()));
        } else {
            envelope.put("stored", true);
            envelope.put("characters", observation.result().length());
            envelope.put("message", "完整结果已保存，请调用readStoredResult按页读取；不得将预览视为完整结果");
            envelope.put("preview", observation.result().substring(0, 1000));
        }
        return envelope.toString();
    }

    private static JsonNode parse(String text) {
        try { return JSON.readTree(text); }
        catch (Exception ignored) { return JSON.getNodeFactory().textNode(text); }
    }

    private static boolean failed(String text) {
        if (text.startsWith("ERROR:")) return true;
        return failedNode(parse(text));
    }

    private static boolean failedNode(JsonNode node) {
        if (node == null) return true;
        if (node.isArray()) { for (var item : node) if (failedNode(item)) return true; }
        if (!node.isObject()) return false;
        return node.hasNonNull("error") || (node.has("available") && !node.path("available").asBoolean())
                || (node.has("success") && !node.path("success").asBoolean())
                || (node.has("indexed") && !node.path("indexed").asBoolean())
                || Set.of("FAILED", "INVALID", "UNKNOWN").contains(node.path("status").asText());
    }
}
