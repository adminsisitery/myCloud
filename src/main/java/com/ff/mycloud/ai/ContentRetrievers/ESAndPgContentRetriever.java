package com.ff.mycloud.ai.ContentRetrievers;

import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.model.entity.FileDocument;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ES 关键词 + PGVector 语义 混合检索器。
 * <p>
 * PGVector 语义分数为主，ES 关键词命中作为 boost：同一 fileId 被 ES 命中时，向量分数上浮。
 * 构造时传入 userId，避免运行时依赖 ThreadLocal。
 * </p>
 */
public class ESAndPgContentRetriever implements ContentRetriever {

    private static final double ES_BOOST = 0.15;  // ES 命中时的加分幅度

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final FileSearchRepository fileSearchRepository;
    private final Long userId;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;
    private final String displayName;

    public ESAndPgContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                   EmbeddingModel embeddingModel,
                                   FileSearchRepository fileSearchRepository,
                                   Long userId,
                                   int maxResults,
                                   double minScore,
                                   Filter filter,
                                   String displayName) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.fileSearchRepository = fileSearchRepository;
        this.userId = userId;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.displayName = displayName;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String keyword = query.text();

        // ===== 1. ES 关键词检索 =====
        // FileSearchRepository 不返回分数，只取 fileId 集合用于后续 boost
        Set<Long> esHitFileIds = new HashSet<>();
        List<FileDocument> byFileName = fileSearchRepository.findByFileNameContainingAndUserId(keyword, userId);
        List<FileDocument> byContent = fileSearchRepository.findByContentContainingAndUserId(keyword, userId);
        if (byFileName != null) byFileName.forEach(d -> esHitFileIds.add(d.getId()));
        if (byContent != null) byContent.forEach(d -> esHitFileIds.add(d.getId()));

        // ===== 2. PGVector 语义检索（有分数） =====
        Embedding embeddedQuery = embeddingModel.embed(query.text()).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> vectorResult = embeddingStore.search(searchRequest);

        // ===== 3. 融合：向量分数为主，ES 命中给 boost =====
        List<Content> contents = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : vectorResult.matches()) {
            TextSegment segment = match.embedded();
            double vectorScore = match.score();

            // ES boost：同一 fileId 被 ES 命中，分数上浮
            String fileIdStr = segment.metadata().getString("fileId");
            boolean esHit = fileIdStr != null && esHitFileIds.contains(Long.valueOf(fileIdStr));
            double finalScore = esHit ? Math.min(1.0, vectorScore + ES_BOOST) : vectorScore;

            segment.metadata().put("hybrid_score", String.format("%.4f", finalScore));
            segment.metadata().put("source", esHit ? "es+pgvector" : "pgvector");
            contents.add(Content.from(segment));
        }

        // ===== 4. ES 独有结果（PGVector 没召回的），追加到末尾 =====
        Set<String> pgFileIds = vectorResult.matches().stream()
                .map(m -> m.embedded().metadata().getString("fileId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<FileDocument> esOnlyDocs = new ArrayList<>();
        if (byContent != null) {
            esOnlyDocs.addAll(byContent.stream()
                    .filter(d -> !pgFileIds.contains(String.valueOf(d.getId())))
                    .collect(Collectors.toList()));
        }

        for (FileDocument doc : esOnlyDocs) {
            if (doc.getContent() != null && !doc.getContent().isBlank()) {
                TextSegment segment = TextSegment.from(doc.getContent());
                segment.metadata().put("fileId", String.valueOf(doc.getId()));
                segment.metadata().put("fileName", doc.getFileName());
                segment.metadata().put("hybrid_score", "0.50");
                segment.metadata().put("source", "es_only");
                contents.add(Content.from(segment));
            }
        }

        // ===== 5. 按 hybrid_score 降序排列 =====
        contents.sort((a, b) -> {
            double sa = Double.parseDouble(
                    a.textSegment().metadata().getString("hybrid_score"));
            double sb = Double.parseDouble(
                    b.textSegment().metadata().getString("hybrid_score"));
            return Double.compare(sb, sa);
        });

        return contents;
    }
}
