package com.cloudrive.aiService.retrieval;

import com.cloudrive.aiService.ES.FileSearchRepository;
import com.cloudrive.aiService.model.entity.FileDocument;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.data.domain.PageRequest;

import java.util.*;

/**
 * Bounded RRF retrieval. File authorization is rechecked by the calling chat service.
 */
public class ESAndPgContentRetriever implements ContentRetriever {
    private final EmbeddingStore<TextSegment> vectors;
    private final EmbeddingModel embeddings;
    private final FileSearchRepository search;
    private final Long userId;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

    public ESAndPgContentRetriever(EmbeddingStore<TextSegment> vectors, EmbeddingModel embeddings,
                                   FileSearchRepository search, Long userId, int maxResults,
                                   double minScore, Filter filter, String displayName) {
        this.vectors = vectors;
        this.embeddings = embeddings;
        this.search = search;
        this.userId = userId;
        this.maxResults = Math.max(1, Math.min(maxResults, 20));
        this.minScore = minScore;
        this.filter = filter;
    }

    private static final class Candidate {
        final String id;
        String name = "";
        double score;
        final Set<String> fragments = new LinkedHashSet<>();

        Candidate(String id) {
            this.id = id;
        }
    }

    @Override
    public List<Content> retrieve(Query query) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        RuntimeException keywordFailure = null;
        try {
            collectKeywords(query.text(), candidates);
        } catch (RuntimeException error) {
            keywordFailure = error;
        }
        try {
            collectVectors(query.text(), candidates);
        } catch (RuntimeException error) {
            if (keywordFailure != null || search == null || candidates.values().stream().allMatch(c -> c.fragments.isEmpty()))
                throw new IllegalStateException("检索服务暂不可用", error);
        }
        var ranked = candidates.values().stream().sorted(Comparator.comparingDouble((Candidate c) -> c.score).reversed()).toList();
        List<Content> result = new ArrayList<>();
        int remaining = 6000;
        // Round robin keeps a second relevant segment without letting one file consume every slot.
        for (int part = 0; part < 2; part++)
            for (Candidate candidate : ranked) {
                var fragments = new ArrayList<>(candidate.fragments);
                if (part >= fragments.size() || result.size() >= maxResults || remaining <= 200) continue;
                String label = "[" + candidate.name + "](file:" + candidate.id + ")\n范围：相关片段，非全文\n";
                String body = fragments.get(part);
                int length = Math.min(body.length(), Math.min(1200 - label.length(), remaining - label.length()));
                if (length <= 0) continue;
                TextSegment segment = TextSegment.from(label + body.substring(0, length));
                segment.metadata().put("fileId", candidate.id).put("fileName", candidate.name).put("source", "hybrid_fragment");
                result.add(Content.from(segment));
                remaining -= segment.text().length();
            }
        return result;
    }

    private void collectKeywords(String keyword, Map<String, Candidate> candidates) {
        if (search == null) return;
        var page = PageRequest.of(0, Math.min(40, maxResults * 2));
        List<FileDocument> documents = new ArrayList<>();
        var names = search.findByFileNameContainingAndUserId(keyword, userId, page);
        var contents = search.findByContentContainingAndUserId(keyword, userId, page);
        if (names != null) documents.addAll(names);
        if (contents != null) documents.addAll(contents);
        Set<Long> seen = new HashSet<>();
        for (FileDocument document : documents) {
            if (document.getId() == null || !Objects.equals(userId, document.getUserId()) || !seen.add(document.getId()))
                continue;
            Candidate candidate = candidates.computeIfAbsent(document.getId().toString(), Candidate::new);
            candidate.score += 1.0 / (60 + seen.size());
            candidate.name = boundedName(document.getFileName());
            if (document.getContent() != null && !document.getContent().isBlank()) {
                String text = document.getContent();
                int start = Math.max(0, text.indexOf(keyword) - 300);
                candidate.fragments.add(text.substring(start, Math.min(text.length(), start + 1200)));
            }
        }
    }

    private void collectVectors(String question, Map<String, Candidate> candidates) {
        var result = vectors.search(EmbeddingSearchRequest.builder().queryEmbedding(embeddings.embed(question).content())
                .filter(filter).maxResults(maxResults * 2).minScore(minScore).build());
        int rank = 0;
        Set<String> rankedFiles = new HashSet<>();
        for (var match : result.matches()) {
            rank++;
            var segment = match.embedded();
            if (segment == null || segment.text().isBlank()) continue;
            String id = segment.metadata().getString("fileId");
            if (id == null || !id.matches("[1-9][0-9]{0,18}")) continue;
            Candidate candidate = candidates.computeIfAbsent(id, Candidate::new);
            if (rankedFiles.add(id)) candidate.score += 1.0 / (60 + rank);
            String name = segment.metadata().getString("fileName");
            if (name != null) candidate.name = boundedName(name);
            if (candidate.fragments.size() < 3)
                candidate.fragments.add(segment.text().substring(0, Math.min(1200, segment.text().length())));
        }
    }

    private String boundedName(String name) {
        String safe = Objects.toString(name, "文件").replace('\n', ' ').replace('\r', ' ');
        return safe.substring(0, Math.min(200, safe.length()));
    }
}
