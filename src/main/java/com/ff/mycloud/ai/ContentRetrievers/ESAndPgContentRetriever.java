package com.ff.mycloud.ai.ContentRetrievers;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.List;

public class ESAndPgContentRetriever implements ContentRetriever {
    @Override
    public List<Content> retrieve(Query query) {
        return List.of();
    }
}
