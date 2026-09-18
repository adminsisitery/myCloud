package com.ff.mycloud.ai.memory;

import com.ff.mycloud.ai.agent.AgentOrchestrator;
import com.ff.mycloud.ai.chat.AiChatServiceImpl;
import com.ff.mycloud.ai.chat.dto.ChatRequest;
import com.ff.mycloud.ai.config.PromptService;
import com.ff.mycloud.ai.model.ModelRegistry;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.FileInfoMapperService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.*;
import dev.langchain4j.store.embedding.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real AiServices streaming/tool/memory pipeline with a deterministic provider. */
class ChatMemoryFlowTest extends ConversationMemoryTest {
    AiChatServiceImpl chat;
    OpenAiStreamingChatModel streaming;
    List<dev.langchain4j.model.chat.request.ChatRequest> requests;
    FileInfoMapperService files;
    EmbeddingStore<TextSegment> vectors;
    AtomicInteger generations;
    @BeforeEach void flowSetup() {
        streaming=mock(OpenAiStreamingChatModel.class);var models=mock(ModelRegistry.class);
        when(models.requireModelName(any())).thenReturn("test");when(models.getStreamingModel("test")).thenReturn(streaming);
        var prompts=mock(PromptService.class);when(prompts.getChatPrompt()).thenReturn("TEST_SYSTEM_POLICY");
        var embeddings=mock(EmbeddingModel.class);when(embeddings.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        vectors=mock(EmbeddingStore.class);when(vectors.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of()));
        files=mock(FileInfoMapperService.class);requests=new CopyOnWriteArrayList<>();generations=new AtomicInteger();
        doAnswer(inv->{
            requests.add(inv.getArgument(0));int n=generations.incrementAndGet();StreamingChatResponseHandler handler=inv.getArgument(1);
            handler.onPartialResponse("新回答"+n);handler.onCompleteResponse(reply("新回答"+n,FinishReason.STOP));return null;
        }).when(streaming).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),any());
        chat=new AiChatServiceImpl(json,models,prompts,files,embeddings,vectors,null,mock(AgentOrchestrator.class),null,repository,service,cache,config,tokens);
    }
    @AfterEach void stop(){chat.shutdown();}
    ChatResponse reply(String text,FinishReason finish){return ChatResponse.builder().aiMessage(AiMessage.from(text)).metadata(ChatResponseMetadata.builder().finishReason(finish).tokenUsage(new TokenUsage(7,3)).build()).build();}
    void send(String input) {
        var request=new ChatRequest();request.setConversationId(10L);request.setMessage(input);
        try(var user=mockStatic(UserContext.class)){user.when(UserContext::getCurrentUserId).thenReturn(1L);chat.sendMessage(request);}
    }
    void awaitTurns(int expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            int count=jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_chat_turn WHERE status<>'RUNNING'",Integer.class);
            if(count>=expected)return;Thread.sleep(10);
        }
        fail("Chat did not finish: "+jdbc.queryForList("SELECT status,error_code FROM t_ai_chat_turn"));
    }
    @Test void identicalQuestionsGenerateNewAnswersWithHistoryAndSingleCurrentUser() throws Exception {
        send("你好");awaitTurns(1);send("你好");awaitTurns(2);
        assertEquals(2,generations.get());assertEquals(4,rows());assertEquals(2,jdbc.queryForObject("SELECT count FROM t_ai_conversation WHERE id=10",Integer.class));
        assertEquals(20,jdbc.queryForObject("SELECT total_tokens FROM t_ai_conversation WHERE id=10",Integer.class));
        assertEquals(2,requests.getFirst().messages().size());assertEquals(4,requests.getLast().messages().size());
        assertTrue(requests.getLast().messages().toString().contains("新回答1"));assertTrue(requests.getFirst().messages().toString().contains("TEST_SYSTEM_POLICY"));
        assertEquals(List.of("你好","新回答1","你好","新回答2"),jdbc.queryForList("SELECT content FROM t_ai_message ORDER BY id",String.class));
    }
    @Test void retrievedEvidenceReachesModelWithSourceButRawInputStaysClean() throws Exception {
        var segment=TextSegment.from("EVIDENCE_合同金额123456元。"+"详细条款".repeat(1000));segment.metadata().put("fileId","7");
        var match=new EmbeddingMatch<TextSegment>(0.95,"segment",Embedding.from(new float[]{1,0}),segment);
        when(vectors.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of(match)));
        var file=new FileInfo();file.setId(7L);file.setUserId(1L);file.setFilename("合同");file.setIsDeleted(false);when(files.getById(7L)).thenReturn(file);
        send("总结合同");awaitTurns(1);
        String context=requests.getFirst().messages().toString();assertTrue(context.contains("EVIDENCE_"));assertTrue(context.contains("file:7"));assertTrue(context.length()<3500);
        assertEquals("总结合同",jdbc.queryForObject("SELECT content FROM t_ai_message WHERE role='USER'",String.class));
        assertEquals(1,requests.getFirst().messages().stream().filter(m->m instanceof dev.langchain4j.data.message.UserMessage).count());
    }
    @Test void deletedOrForeignIndexedEvidenceIsNotGivenToModel() throws Exception {
        var segment=TextSegment.from("SECRET_FILE_CONTENT");segment.metadata().put("fileId","7");
        when(vectors.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.95,"s",Embedding.from(new float[]{1,0}),segment))));
        var file=new FileInfo();file.setUserId(2L);file.setIsDeleted(false);when(files.getById(7L)).thenReturn(file);
        send("总结");awaitTurns(1);assertFalse(requests.getFirst().messages().toString().contains("SECRET_FILE_CONTENT"));
    }
    @Test void historyToolActuallyReadsDatabaseAndReturnsIntoModelContext() throws Exception {
        var old=begin("约定预算为31415元");repository.complete(old,"记录好了",List.of(),"test",0,0,1);
        doAnswer(inv->{
            requests.add(inv.getArgument(0));StreamingChatResponseHandler handler=inv.getArgument(1);
            if(generations.getAndIncrement()==0) handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder().id("call1").name("searchConversationHistory").arguments("{\"keyword\":\"预算\",\"beforeMessageId\":0}").build())).build());
            else {handler.onPartialResponse("预算是31415元");handler.onCompleteResponse(reply("预算是31415元",FinishReason.STOP));}return null;
        }).when(streaming).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),any());
        send("预算多少");awaitTurns(2);assertEquals(2,generations.get());
        assertTrue(requests.getLast().messages().stream().anyMatch(m->m instanceof dev.langchain4j.data.message.ToolExecutionResultMessage && m.toString().contains("31415")));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT status FROM t_ai_chat_turn ORDER BY user_message_id DESC LIMIT 1",String.class));
        assertTrue(jdbc.queryForObject("SELECT messages FROM t_ai_chat_turn ORDER BY user_message_id DESC LIMIT 1",String.class).contains("call1"));
    }
    @Test void outputCutOffIsFailedAndNeverStoredAsSuccessfulAssistant() throws Exception {
        doAnswer(inv->{StreamingChatResponseHandler h=inv.getArgument(1);h.onPartialResponse("半句话");h.onCompleteResponse(reply("半句话",FinishReason.LENGTH));return null;})
                .when(streaming).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),any());
        send("回答");awaitTurns(1);assertEquals(1,rows());assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM t_ai_chat_turn",String.class));
        assertEquals("OUTPUT_TRUNCATED",jdbc.queryForObject("SELECT error_code FROM t_ai_chat_turn",String.class));
    }
    @Test void inventedCitationIsNotArchivedAsSuccessfulAnswer() throws Exception {
        doAnswer(inv->{StreamingChatResponseHandler h=inv.getArgument(1);h.onPartialResponse("[编造](file:999)");h.onCompleteResponse(reply("[编造](file:999)",FinishReason.STOP));return null;})
                .when(streaming).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class),any());
        send("总结合同");awaitTurns(1);
        assertEquals(1,rows());assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM t_ai_chat_turn",String.class));
        assertEquals("INVALID_CITATION",jdbc.queryForObject("SELECT error_code FROM t_ai_chat_turn",String.class));
    }
}
