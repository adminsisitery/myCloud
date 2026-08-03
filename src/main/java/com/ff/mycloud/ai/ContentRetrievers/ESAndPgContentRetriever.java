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
 * ES 关键词 + PGVector 语义 混合检索器 —— RRF（Reciprocal Rank Fusion）融合排序。
 * <p>
 * 每个来源独立排名，然后按 RRF 公式融合：
 *   RRF_score = Σ 1/(k + rank_in_source)    (k=60)
 * 两个来源都命中的文档得分更高，单来源命中也有公平机会。
 * 构造时传入 userId，避免运行时依赖 ThreadLocal。
 * </p>
 */
public class ESAndPgContentRetriever implements ContentRetriever {

    private static final double RRF_K = 60.0;  // RRF 平滑常数

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

        // ===== 1. ES 关键词检索（带 rank） =====
        // 文件名匹配 > 内容匹配，按此顺序排 rank
        Map<Long, Integer> esRankMap = new LinkedHashMap<>(); // preserve insertion order = rank
        List<FileDocument> byFileName = fileSearchRepository.findByFileNameContainingAndUserId(keyword, userId);
        List<FileDocument> byContent = fileSearchRepository.findByContentContainingAndUserId(keyword, userId);
        if (byFileName != null) byFileName.forEach(d -> esRankMap.putIfAbsent(d.getId(), esRankMap.size() + 1));
        if (byContent != null) byContent.forEach(d -> esRankMap.putIfAbsent(d.getId(), esRankMap.size() + 1));

        // ===== 2. PGVector 语义检索（带 rank，按相似度降序） =====
        Embedding embeddedQuery = embeddingModel.embed(query.text()).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> vectorResult = embeddingStore.search(searchRequest);

        // PGVector rank：按匹配顺序（已按分数降序），rank 从 1 开始
        Map<String, Integer> pgRankMap = new LinkedHashMap<>(); // fileId -> rank
        Map<String, TextSegment> pgSegmentMap = new LinkedHashMap<>(); // fileId -> best segment
        int rank = 0;
        for (EmbeddingMatch<TextSegment> match : vectorResult.matches()) {
            rank++;
            String fileId = match.embedded().metadata().getString("fileId");
            if (fileId != null) {
                pgRankMap.putIfAbsent(fileId, rank);
                pgSegmentMap.putIfAbsent(fileId, match.embedded());
            }
        }

        // ===== 3. RRF 融合 =====
        // 收集所有出现的 fileId
        Set<String> allFileIds = new LinkedHashSet<>();
        esRankMap.keySet().forEach(id -> allFileIds.add(String.valueOf(id)));
        allFileIds.addAll(pgRankMap.keySet());

        // 计算每个 fileId 的 RRF 分数
        record ScoredDoc(String fileId, String fileName, String content,
                         double rrfScore, boolean fromES, boolean fromPG) {}
        List<ScoredDoc> scored = new ArrayList<>();

        for (String fid : allFileIds) {
            double rrf = 0.0;
            boolean fromES = false, fromPG = false;

            // ES rank contribution
            Long fidLong = Long.valueOf(fid);
            Integer esR = esRankMap.get(fidLong);
            if (esR != null) {
                rrf += 1.0 / (RRF_K + esR);
                fromES = true;
            }

            // PGVector rank contribution
            Integer pgR = pgRankMap.get(fid);
            if (pgR != null) {
                rrf += 1.0 / (RRF_K + pgR);
                fromPG = true;
            }

            // 获取文本内容：优先 PGVector 的 segment，否则从 ES doc 取
            TextSegment segment = pgSegmentMap.get(fid);
            String text = null;
            String fileName = null;
            if (segment != null) {
                text = segment.text();
                fileName = segment.metadata().getString("fileName");
            }
            if (text == null && fromES) {
                // 从 ES 结果中拿文本
                for (FileDocument d : byContent != null ? byContent : List.<FileDocument>of()) {
                    if (d.getId().equals(fidLong) && d.getContent() != null) {
                        text = d.getContent();
                        break;
                    }
                }
                if (text == null && byFileName != null) {
                    for (FileDocument d : byFileName) {
                        if (d.getId().equals(fidLong) && d.getContent() != null) {
                            text = d.getContent();
                            break;
                        }
                    }
                }
                if (fileName == null && byFileName != null) {
                    for (FileDocument d : byFileName) {
                        if (d.getId().equals(fidLong)) {
                            fileName = d.getFileName();
                            break;
                        }
                    }
                }
            }
            if (text == null || text.isBlank()) continue;

            String source = (fromES && fromPG) ? "es+pgvector" : fromPG ? "pgvector" : "es_only";
            scored.add(new ScoredDoc(fid, fileName, text, rrf, fromES, fromPG));
        }

        // ===== 4. 按 RRF 分数降序排列 =====
        scored.sort((a, b) -> Double.compare(b.rrfScore, a.rrfScore));

        // ===== 5. 转为 Content =====
        List<Content> contents = new ArrayList<>();
        for (ScoredDoc doc : scored) {
            TextSegment segment = TextSegment.from(doc.content);
            segment.metadata().put("fileId", doc.fileId);
            segment.metadata().put("fileName", doc.fileName != null ? doc.fileName : "");
            segment.metadata().put("rrf_score", String.format("%.6f", doc.rrfScore));
            segment.metadata().put("source",
                    (doc.fromES && doc.fromPG) ? "es+pgvector" : doc.fromPG ? "pgvector" : "es_only");
            contents.add(Content.from(segment));
        }

        return contents;
    }
}
