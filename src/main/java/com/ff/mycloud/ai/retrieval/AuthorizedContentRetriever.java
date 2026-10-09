package com.ff.mycloud.ai.retrieval;

import com.ff.mycloud.service.FileInfoMapperService;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.*;
import java.util.function.Consumer;

/**
 * All model-visible file evidence crosses this live permission and length boundary.
 */
public final class AuthorizedContentRetriever implements ContentRetriever {
    private final ContentRetriever delegate;
    private final FileInfoMapperService files;
    private final Long userId;
    private final Runnable check;
    private final Consumer<Long> recordSource;

    public AuthorizedContentRetriever(ContentRetriever delegate, FileInfoMapperService files, Long userId,
                                      Runnable check, Consumer<Long> recordSource) {
        this.delegate = delegate;
        this.files = files;
        this.userId = userId;
        this.check = check;
        this.recordSource = recordSource;
    }

    @Override
    public List<Content> retrieve(Query query) {
        check.run();
        try {
            List<Content> result = new ArrayList<>();
            for (Content content : delegate.retrieve(query)) {
                String id = content.textSegment().metadata().getString("fileId");
                Long fileId;
                try {
                    fileId = Long.valueOf(id);
                } catch (NumberFormatException invalid) {
                    continue;
                }
                var file = files.getById(fileId);
                if (file == null || !Objects.equals(file.getUserId(), userId) || Boolean.TRUE.equals(file.getIsDeleted()))
                    continue;
                String text = content.textSegment().text();
                if (text == null || text.isBlank()) continue;
                String name = Objects.toString(file.getFilename(), "文件").replaceAll("[\\r\\n\\[\\]]", " ");
                name = name.substring(0, Math.min(200, name.length()));
                String label = "来源：[" + name + "](file:" + fileId + ")；仅为检索片段，不代表全文。\n";
                result.add(Content.from(label + text.substring(0, Math.min(1200 - label.length(), text.length()))));
                recordSource.accept(fileId);
                if (result.size() == 5) break;
            }
            check.run();
            return result;
        } catch (Exception unavailable) {
            check.run();
            return List.of(Content.from("检索状态：文件知识库暂不可用。不能声称已检索成功或文件不存在；请告知用户暂时无法核实文件内容。"));
        }
    }
}
