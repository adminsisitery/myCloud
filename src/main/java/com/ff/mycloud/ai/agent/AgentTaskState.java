package com.ff.mycloud.ai.agent;

import java.util.*;
import lombok.Data;

/** Durable task data, separate from the small context sent to the model. */
@Data
public class AgentTaskState {
    private List<String> instructions = new ArrayList<>();
    private Map<String, Observation> observations = new LinkedHashMap<>();
    private Map<Long, DocumentProgress> documents = new LinkedHashMap<>();
    private Map<String, Draft> drafts = new LinkedHashMap<>();
    private boolean historyIncomplete;

    public record Observation(String id, String tool, String arguments, String result, boolean success) {}
    public record DocumentProgress(String version, int readThrough, int length) {
        public boolean complete() { return length > 0 && readThrough >= length; }
    }
    public record Draft(Long parentId, String name, String content) {}

    public void recordPage(Long fileId, String version, int start, int end, int length) {
        DocumentProgress previous = documents.get(fileId);
        int through = previous != null && previous.version().equals(version) ? previous.readThrough() : 0;
        if (length < 0 && previous != null && previous.version().equals(version)) return;
        if (start <= through) through = Math.max(through, end);
        documents.put(fileId, new DocumentProgress(version, through, length));
    }
}
