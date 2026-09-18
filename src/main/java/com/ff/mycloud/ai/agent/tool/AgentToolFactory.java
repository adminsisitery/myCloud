package com.ff.mycloud.ai.agent.tool;

import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.ai.agent.AgentRun;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.ai.tool.WebSearchTool;
import com.ff.mycloud.service.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AgentToolFactory {
    private final FileInfoMapperService files;
    private final UserCenterMapperService users;
    private final AgentActionService actions;
    private final ObjectProvider<FileSearchRepository> search;
    private final EmbeddingModel embeddings;
    private final EmbeddingStore<TextSegment> vectors;
    private final ObjectProvider<WebSearchEngine> web;
    private final AgentDocumentReader reader;
    public AgentToolFactory(FileInfoMapperService files, UserCenterMapperService users, AgentActionService actions,
                            ObjectProvider<FileSearchRepository> search, EmbeddingModel embeddings, EmbeddingStore<TextSegment> vectors, ObjectProvider<WebSearchEngine> web, AgentDocumentReader reader) {
        this.files=files; this.users=users; this.actions=actions; this.search=search; this.embeddings=embeddings; this.vectors=vectors; this.web=web;
        this.reader=reader;
    }
    public List<Object> create(AgentRun run, boolean webEnabled) {
        List<Object> tools = new ArrayList<>();
        tools.add(new AgentTools(run, files, users, actions, search.getIfAvailable(), embeddings, vectors, reader));
        if (webEnabled) {
            var engine = web.getIfAvailable();
            if (engine != null) tools.add(new WebSearchTool(engine));
        }
        return tools;
    }
}
