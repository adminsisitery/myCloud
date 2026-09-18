package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic provenance checks, not a claim that semantic hallucinations are eliminated. */
final class AgentAnswerVerifier {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CITATION = Pattern.compile("file:(\\d+)");
    private static final Pattern WRITE_CLAIM = Pattern.compile("(?i)(?:已(?:经)?|成功|完成).{0,10}(?:创建|移动|删除|重命名|修改|保存|恢复|收藏)|(?:created|moved|deleted|renamed|saved)\\s+(?:the|your)\\b");
    private static final Pattern FULL_READ_CLAIM = Pattern.compile("(?:已|完成).{0,8}(?:全文|全部内容|整个文档)|(?:全文|全部内容).{0,6}(?:读完|阅读完)|(?i)read (?:the )?(?:entire|whole) (?:file|document)");

    String verify(AgentRun run, String raw, Set<String> failedTools, boolean toolsAvailable) {
        if (raw == null || raw.isBlank()) throw new AgentExecutionException("EMPTY_MODEL_RESPONSE");
        if (!failedTools.isEmpty()) throw new AgentExecutionException("TOOL_RESULT_UNVERIFIED");
        JsonNode structured = parseObject(raw);
        String answer = structured == null ? raw : structured.path("answer").asText("");
        if (answer.isBlank()) throw new AgentExecutionException("INVALID_FINAL_RESPONSE");
        if (structured != null && !"COMPLETED".equals(structured.path("status").asText())) throw new AgentExecutionException("TASK_NOT_COMPLETED");
        boolean hasEvidence = run.getTaskState().getObservations().values().stream().anyMatch(AgentTaskState.Observation::success);
        if (toolsAvailable && !hasEvidence) throw new AgentExecutionException("MISSING_TOOL_EVIDENCE");
        if (WRITE_CLAIM.matcher(answer).find()) throw new AgentExecutionException("UNVERIFIED_WRITE_CLAIM");
        if (FULL_READ_CLAIM.matcher(answer).find() && (run.getTaskState().getDocuments().isEmpty()
                || run.getTaskState().getDocuments().values().stream().anyMatch(p -> !p.complete()))) {
            throw new AgentExecutionException("INCOMPLETE_DOCUMENT_READ");
        }
        var citations = CITATION.matcher(answer);
        while (citations.find()) {
            long fileId;
            try { fileId = Long.parseLong(citations.group(1)); }
            catch (NumberFormatException error) { throw new AgentExecutionException("INVALID_FILE_CITATION"); }
            if (!run.getSourceFileIds().contains(fileId)) throw new AgentExecutionException("INVALID_FILE_CITATION");
            if (!quotedEvidence(structured, run, fileId)) throw new AgentExecutionException("MISSING_CITATION_EVIDENCE");
        }
        return answer;
    }

    private boolean quotedEvidence(JsonNode response, AgentRun run, long fileId) {
        if (response == null || !response.path("evidence").isArray()) return false;
        for (var evidence : response.path("evidence")) {
            if (evidence.path("fileId").asLong(-1) != fileId) continue;
            String quote = evidence.path("quote").asText("");
            if (quote.length() < 4 || quote.length() > 1000) continue;
            for (var observation : run.getTaskState().getObservations().values()) {
                if (observation.success() && containsSourceQuote(parseObjectOrArray(observation.result()), fileId, quote, false)) return true;
            }
        }
        return false;
    }

    private boolean containsSourceQuote(JsonNode node, long fileId, String quote, boolean sourceMatches) {
        if (node == null) return false;
        if (node.isObject()) {
            boolean matches = node.has("fileId") ? node.path("fileId").asLong(-1) == fileId : sourceMatches;
            if (matches && (node.path("content").asText("").contains(quote) || node.path("excerpt").asText("").contains(quote))) return true;
            for (var child : node) if (containsSourceQuote(child, fileId, quote, matches)) return true;
        } else if (node.isArray()) {
            for (var child : node) if (containsSourceQuote(child, fileId, quote, sourceMatches)) return true;
        }
        return false;
    }

    private JsonNode parseObject(String value) {
        JsonNode parsed = parseObjectOrArray(value);
        return parsed != null && parsed.isObject() ? parsed : null;
    }

    private JsonNode parseObjectOrArray(String value) {
        try { return JSON.readTree(value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "")); }
        catch (Exception ignored) { return null; }
    }
}
