package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ES.FileSearchRepository;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.ai.agent.plan.AgentPlanParser;
import com.ff.mycloud.ai.agent.tool.AgentDocumentReader;
import com.ff.mycloud.ai.agent.tool.AgentTools;
import com.ff.mycloud.ai.memory.DbChatMemoryStore;
import com.ff.mycloud.ai.persistence.mapper.AiConversationMapper;
import com.ff.mycloud.ai.persistence.mapper.AiMessageMapper;
import com.ff.mycloud.ai.retrieval.ESAndPgContentRetriever;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import com.ff.mycloud.service.UserCenterMapperService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression tests: the former audit counterexamples must now be rejected or handled correctly. */
class AiFlowAuditReproductionTest {
    final AgentProperties config = new AgentProperties();
    final AgentRunStore store = mock(AgentRunStore.class);
    final ChatModel model = mock(ChatModel.class);
    final AgentRun run = new AgentRun();
    final AgentEngine engine;

    AiFlowAuditReproductionTest() {
        run.setUserId(1L); run.setConversationId(10L); run.setMessage("总结这份文档");
        run.setDeadline(LocalDateTime.now().plusMinutes(5));
        when(store.save(any())).thenReturn(true);
        engine = new AgentEngine(config, new AgentPlanParser(new ObjectMapper()), store);
    }

    void execute(List<Object> tools, List<ChatMessage> history) {
        engine.execute(run, model, tools, history, (n,d) -> {});
    }
    static ChatResponse answer(String text) { return AgentEngineTest.answer(text); }
    static ChatResponse tool(String name, String args) {
        return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString()).name(name).arguments(args).build())).build();
    }

    @Test void planningReceivesPriorUserConstraints() {
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"总结\"]}"), answer("完成"));
        execute(List.of(), List.of(UserMessage.from("必须保留项目甲，禁止处理项目乙")));
        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(2)).chat(requests.capture());
        assertTrue(requests.getAllValues().getFirst().messages().toString().contains("禁止处理项目乙"));
        assertTrue(requests.getAllValues().getLast().messages().toString().contains("禁止处理项目乙"));
    }

    @Test void inventedFileCitationAndCompletionAreRejectedWithoutAnyRead() {
        String fabricated = "我已读完全文并完成修改，收入为999万元。[报告](file:999999)";
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"总结\"]}"), answer(fabricated));
        assertThrows(AgentExecutionException.class, () -> execute(List.of(), List.of()));
        assertEquals("", run.getOutput());
        assertNotEquals("DONE", run.getPlan().getSteps().getFirst().getStatus());
        assertTrue(run.getSourceFileIds().isEmpty());
        assertEquals(0, run.getToolCalls());
    }

    @Test void failedToolCannotBeReportedAsSuccessful() {
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"读取\"]}"),
                tool("broken", "{}"), answer("读取成功，文档结论是批准上线"));
        assertThrows(AgentExecutionException.class, () -> execute(List.of(new AgentEngineTest.BrokenTool()), List.of()));
        assertNotEquals("DONE", run.getPlan().getSteps().getFirst().getStatus());
        assertFalse(run.getOutput().contains("读取成功"));
    }

    public static class DocumentTools {
        int reads, drafts;
        @Tool("read next document page") public String readPage() { reads++; return "页".repeat(6000); }
        @Tool("draft up to 30000 characters") public String draftDocument(String content) { drafts++; return "drafted"; }
    }

    @Test void readingEightPagesArchivesOldExchangesWithoutLosingEvidence() {
        var tools = new DocumentTools();
        config.setMaxTotalTokens(300000);
        when(model.chat(any(ChatRequest.class))).thenAnswer(inv -> run.getModelCalls() == 1
                ? answer("{\"steps\":[\"读取长文档\"]}") : tools.reads < 8 ? tool("readPage", "{}") : answer("已获取八页材料"));
        execute(List.of(tools), List.of());
        assertEquals(8, tools.reads);
        assertEquals(8, run.getTaskState().getObservations().size());
        assertEquals(48000, run.getTaskState().getObservations().values().stream().mapToInt(o -> o.result().length()).sum());
        assertEquals("DONE", run.getPlan().getSteps().getFirst().getStatus());
        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(10)).chat(requests.capture());
        assertTrue(requests.getAllValues().getLast().messages().toString().contains("readStoredResult"));
    }

    @Test void advertised9000CharacterDraftReachesTool() throws Exception {
        var tools = new DocumentTools();
        String args = new ObjectMapper().writeValueAsString(Map.of("content", "文".repeat(9000)));
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"保存报告\"]}"),
                tool("draftDocument", args), answer("参数过长"));
        execute(List.of(tools), List.of());
        assertEquals(1, tools.drafts);
        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(3)).chat(requests.capture());
        assertFalse(requests.getAllValues().getLast().messages().toString().contains("工具参数过长"));
    }

    @Test void slidingMemoryWindowPreservesPersistentConversationHistory() {
        var rows = mock(AiMessageMapper.class);
        var conversations = mock(AiConversationMapper.class);
        List<com.ff.mycloud.ai.persistence.entity.AiMessage> existing = new ArrayList<>();
        for (int i=1; i<=4; i++) {
            var row = new com.ff.mycloud.ai.persistence.entity.AiMessage();
            row.setId((long)i); row.setRole(i%2 == 1 ? "USER" : "ASSISTANT"); row.setContent("message"+i);
            existing.add(row);
        }
        when(rows.findByMsgId(10L)).thenReturn(existing);
        new DbChatMemoryStore(rows, conversations).updateMessages(10L,
                List.of(UserMessage.from("message3"), AiMessage.from("message4"), UserMessage.from("message5")));
        verifyNoInteractions(rows);
        verify(conversations).updateById(any(com.ff.mycloud.ai.persistence.entity.AiConversation.class));
    }

    @Test @SuppressWarnings("unchecked") void hybridRetrieverEnforcesGlobalTopKAndTextBudget() {
        var search = mock(FileSearchRepository.class);
        var embeddings = mock(EmbeddingModel.class);
        EmbeddingStore<TextSegment> vectors = mock(EmbeddingStore.class);
        List<FileDocument> docs = new ArrayList<>();
        for (long i=1; i<=6; i++) {
            var doc = new FileDocument(); doc.setId(i); doc.setUserId(1L);
            doc.setFileName("report"+i); doc.setContent("正文".repeat(5000)); docs.add(doc);
        }
        when(search.findByFileNameContainingAndUserId(eq("报告"),eq(1L),any(org.springframework.data.domain.Pageable.class))).thenReturn(docs);
        when(search.findByContentContainingAndUserId(eq("报告"),eq(1L),any(org.springframework.data.domain.Pageable.class))).thenReturn(docs);
        when(embeddings.embed("报告")).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        when(vectors.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of()));
        var result = new ESAndPgContentRetriever(vectors, embeddings, search, 1L, 5, 0.6, null, "chat")
                .retrieve(Query.from("报告"));
        assertEquals(5, result.size());
        assertTrue(result.stream().mapToInt(c -> c.textSegment().text().length()).sum() <= 6000);
        assertTrue(result.getFirst().textSegment().text().contains("file:1"));
    }

    @Test void emptyIndexedDocumentFallsBackToOriginalObject() throws Exception {
        var actions = mock(AgentActionService.class);
        var search = mock(FileSearchRepository.class);
        var reader = mock(AgentDocumentReader.class);
        var file = new FileInfo(); file.setId(7L); file.setUserId(1L); file.setFilename("合同"); file.setFileSuffix("txt");
        when(actions.requireFile(7L,1L,false)).thenReturn(file);
        var doc = new FileDocument(); doc.setId(7L); doc.setUserId(1L); doc.setContent("");
        when(search.findById(7L)).thenReturn(Optional.of(doc));
        var tools = new AgentTools(run, mock(FileInfoMapperService.class), mock(UserCenterMapperService.class),
                actions, search, null, null, reader);
        when(reader.read(file)).thenReturn("原始合同正文");
        var result = tools.readDocumentPage(7L,0);
        assertEquals(true, result.get("complete")); assertEquals("原始合同正文", result.get("content"));
        assertEquals(true, result.get("available")); verify(reader).read(file);
    }

    @Test void nearExhaustedBudgetRejectsBeforeCallingModel() {
        run.setPriorTokens(config.getMaxTotalTokens()-1);
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"总结\"]}"));
        assertThrows(IllegalStateException.class, () -> execute(List.of(),List.of()));
        assertTrue(run.getPriorTokens()+run.totalTokens() <= config.getMaxTotalTokens());
        verifyNoInteractions(model);
    }

    @Test void outputCutOffByTokenLimitCannotBeMarkedDone() {
        var truncated = ChatResponse.builder().aiMessage(AiMessage.from("结论如下：第一项"))
                .metadata(dev.langchain4j.model.chat.response.ChatResponseMetadata.builder()
                        .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH).build()).build();
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"总结\"]}"), truncated);
        var error = assertThrows(AgentExecutionException.class, () -> execute(List.of(),List.of()));
        assertEquals("OUTPUT_TRUNCATED", error.getMessage());
        assertNotEquals("DONE",run.getPlan().getSteps().getFirst().getStatus());
        assertEquals("",run.getOutput());
    }

    @Test void ragInjectionPreservesIdentityEmbeddedInEvidenceText() {
        var segment = TextSegment.from("[审计合同.pdf](file:998877)\n合同正文");
        segment.metadata().put("fileId","998877").put("fileName","审计合同.pdf");
        var injected = new dev.langchain4j.rag.content.injector.DefaultContentInjector()
                .inject(List.of(dev.langchain4j.rag.content.Content.from(segment)),UserMessage.from("总结"));
        assertTrue(injected.toString().contains("合同正文"));
        assertTrue(injected.toString().contains("998877"));
        assertTrue(injected.toString().contains("审计合同.pdf"));
    }

    @Test void ragEvidenceIsPreservedBeforeModelReceivesCurrentRequest() {
        var rows = mock(AiMessageMapper.class);
        var conversations = mock(AiConversationMapper.class);
        var conversation = new com.ff.mycloud.ai.persistence.entity.AiConversation();
        conversation.setId(10L);
        when(rows.findByMsgId(10L)).thenReturn(List.of());
        when(conversations.selectById(10L)).thenReturn(conversation);
        doAnswer(inv -> {
            com.ff.mycloud.ai.persistence.entity.AiConversation update = inv.getArgument(0);
            if(update.getMemoryState()!=null) conversation.setMemoryState(update.getMemoryState());
            return 1;
        }).when(conversations).updateById(any(com.ff.mycloud.ai.persistence.entity.AiConversation.class));
        var memory = dev.langchain4j.memory.chat.MessageWindowChatMemory.builder().id(10L).maxMessages(40)
                .chatMemoryStore(new DbChatMemoryStore(rows, conversations)).build();
        var captured = new java.util.concurrent.atomic.AtomicReference<ChatRequest>();
        var streamModel = new dev.langchain4j.model.chat.StreamingChatModel() {
            @Override public void doChat(ChatRequest request, dev.langchain4j.model.chat.response.StreamingChatResponseHandler handler) {
                captured.set(request); handler.onPartialResponse("回答"); handler.onCompleteResponse(answer("回答"));
            }
        };
        String evidence = "EVIDENCE_AUDIT_MARKER:金额为123456元。" + "合同补充条款。".repeat(80);
        var assistant = dev.langchain4j.service.AiServices.builder(com.ff.mycloud.ai.chat.SimpleChatAssistant.class)
                .streamingChatModel(streamModel).systemMessageProvider(id -> "AUDIT_SYSTEM_MARKER")
                .contentRetriever(query -> List.of(dev.langchain4j.rag.content.Content.from(evidence)))
                .chatMemory(memory).build();
        memory.add(UserMessage.from("总结合同"));
        assistant.chat("总结合同").onPartialResponse(t -> {}).onCompleteResponse(r -> {})
                .onError(e -> { throw new AssertionError(e); }).start();
        assertNotNull(captured.get());
        assertTrue(captured.get().messages().toString().contains("EVIDENCE_AUDIT_MARKER"));
    }
}
