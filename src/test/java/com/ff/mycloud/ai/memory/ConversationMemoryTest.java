package com.ff.mycloud.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;

import static com.ff.mycloud.ai.memory.ConversationMemoryRepository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationMemoryTest {
    JdbcTemplate jdbc;
    ConversationMemoryRepository repository;
    ConversationMemoryCache cache;
    ConversationMemoryService service;
    ChatMemoryProperties config;
    ChatModel model;
    ObjectMapper json=new ObjectMapper();
    TokenCountEstimator tokens;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        String schema=Files.readString(Path.of("deploy/001-agent.sql")).split("-- Idempotent upgrade")[0]+Files.readString(Path.of("deploy/003-chat-memory.sql"));
        new ResourceDatabasePopulator(new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8))).execute(source);
        jdbc.update("INSERT INTO t_ai_conversation(id,user_id,title,mode) VALUES(10,1,'chat','CHAT'),(11,2,'other','CHAT'),(12,1,'same user other chat','CHAT')");
        config=new ChatMemoryProperties();repository=spy(new ConversationMemoryRepository(jdbc,new DataSourceTransactionManager(source),config));
        cache=mock(ConversationMemoryCache.class);tokens=mock(TokenCountEstimator.class);model=mock(ChatModel.class);
        when(tokens.estimateTokenCountInText(anyString())).thenAnswer(i->((String)i.getArgument(0)).length());
        service=new ConversationMemoryService(repository,cache,config,tokens,json);
    }
    Turn begin(String input){return repository.begin(1,10,null,input);}
    void history(int count) {
        for(int i=0;i<count;i++){var turn=begin("问题"+i);repository.complete(turn,"回答"+i,List.of(UserMessage.from("问题"+i),AiMessage.from("回答"+i)),"test",2,1,1);}
    }
    String summary(long source) {return "{\"goals\":[{\"text\":\"用户希望整理资料\",\"speaker\":\"USER\",\"sourceMessageIds\":["+source+"]}],\"constraints\":[],\"facts\":[],\"decisions\":[],\"openQuestions\":[]}";}
    ChatResponse answer(String text){return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();}
    int rows(){return jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_message WHERE conversation_id=10",Integer.class);}

    @Test void twentyTurnsCompressTenWithoutDeletingAnyOriginal() {
        history(20);Turn turn=begin("继续");when(model.chat(any(ChatRequest.class))).thenReturn(answer(summary(1)));
        var memory=service.prepare(turn,model,"test");
        assertEquals(41,rows());assertEquals(20,repository.state(1,10).throughId());
        assertEquals(22,memory.messages().size()); // summary pair + ten original turns
        assertTrue(memory.messages().toString().contains("问题10"));assertTrue(memory.messages().toString().contains("问题19"));
        assertEquals("问题0",repository.read(turn,1,0).getFirst().content());
        assertTrue(memory.archive().isEmpty());verify(model,times(1)).chat(any(ChatRequest.class));
    }
    @Test void fewerThanTwentyTurnsRemainVerbatim() {
        history(19);var turn=begin("新消息");var memory=service.prepare(turn,model,"test");
        assertEquals(38,memory.messages().size());assertEquals(0,repository.state(1,10).throughId());verifyNoInteractions(model);
    }
    @Test void longRecentHistoryTriggersCompressionBeforeTwentyTurns() {
        config.setRecentTokenBudget(1000);
        var old=begin("长消息".repeat(500));repository.complete(old,"回复",List.of(),"test",0,0,1);
        when(model.chat(any(ChatRequest.class))).thenReturn(answer(summary(old.userMessageId())));
        service.prepare(begin("继续"),model,"test");assertEquals(2,repository.state(1,10).throughId());assertEquals(3,rows());
    }
    @Test void fabricatedSummaryReferencesDoNotAdvanceCursorOrDeleteHistory() {
        history(20);when(model.chat(any(ChatRequest.class))).thenReturn(answer(summary(999999)));
        assertThrows(ResponseStatusException.class,()->service.prepare(begin("继续"),model,"test"));
        assertEquals(0,repository.state(1,10).throughId());assertNull(repository.state(1,10).summary());assertEquals(41,rows());
    }
    @Test void emptyOrTruncatedSummaryCannotReplaceHistory() {
        history(20);var turn=begin("继续");
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"goals\":[],\"constraints\":[],\"facts\":[],\"decisions\":[],\"openQuestions\":[]}"));
        assertThrows(ResponseStatusException.class,()->service.prepare(turn,model,"test"));
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(summary(1)))
                .metadata(dev.langchain4j.model.chat.response.ChatResponseMetadata.builder().finishReason(dev.langchain4j.model.output.FinishReason.LENGTH).build()).build());
        assertThrows(ResponseStatusException.class,()->service.prepare(turn,model,"test"));assertEquals(0,repository.state(1,10).throughId());
    }
    @Test void cacheMissRecoversHistoryFromDatabaseAndCacheHitAvoidsReload() {
        history(3);var turn=begin("继续");var snapshot=repository.load(turn);clearInvocations(repository);
        service.prepare(turn,model,"test");verify(repository).load(turn);verify(cache).put(1,10,snapshot);
        when(cache.get(turn)).thenReturn(snapshot);clearInvocations(repository);
        assertEquals(6,service.prepare(turn,model,"test").messages().size());verify(repository,never()).load(any());
    }
    @Test void historyAndToolsCannotCrossConversationOrUser() {
        history(1);var secret=repository.begin(2,11,null,"secret");var sameUser=repository.begin(1,12,null,"other-secret");var turn=begin("current");
        assertThrows(ResponseStatusException.class,()->repository.begin(2,10,null,"attack"));
        assertTrue(repository.read(turn,secret.userMessageId(),0).isEmpty());assertTrue(repository.read(turn,sameUser.userMessageId(),0).isEmpty());
        assertTrue(repository.read(turn,turn.userMessageId(),0).isEmpty());assertTrue(repository.search(turn,"secret",Long.MAX_VALUE).isEmpty());
        var tools=new ConversationHistoryTools(repository,turn,1);assertFalse(tools.readConversationMessages(1L,0).containsKey("error"));
        assertTrue(tools.searchConversationHistory("问题",0L).containsKey("error"));
    }
    @Test void searchEscapesWildcardsAndReadPaginatesOriginal() {
        var first=begin("abc%_"+"甲".repeat(5000));repository.fail(first,"test",List.of());var turn=begin("continue");
        assertEquals(1,repository.search(turn,"%_",turn.userMessageId()).size());assertTrue(repository.search(turn,"a_c",turn.userMessageId()).isEmpty());
        assertEquals(2000,repository.read(turn,first.userMessageId(),0).getFirst().content().length());
        assertEquals(1005,repository.read(turn,first.userMessageId(),4000).getFirst().content().length());
    }
    @Test void parallelRequestsAcquireOneDatabaseLease() throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work=()->{ready.countDown();go.await();try{begin("race");return true;}catch(ResponseStatusException e){return false;}};
            var a=executor.submit(work);var b=executor.submit(work);assertTrue(ready.await(2,TimeUnit.SECONDS));go.countDown();
            assertNotEquals(a.get(3,TimeUnit.SECONDS),b.get(3,TimeUnit.SECONDS));assertEquals(1,rows());
        }
    }
    @Test void expiredTurnCannotCompleteOrReleaseNewerLease() {
        var old=begin("old");jdbc.update("UPDATE t_ai_chat_memory SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE conversation_id=10");
        var next=begin("next");assertThrows(ResponseStatusException.class,()->repository.complete(old,"late",List.of(),"test",1,1,1));
        repository.fail(old,"late",List.of());repository.requireActive(next);assertEquals(2,rows());
        assertEquals("TIMED_OUT",jdbc.queryForObject("SELECT status FROM t_ai_chat_turn WHERE id=?",String.class,old.id()));
    }
    @Test void duplicateRequestRollsBackUserRecordAndNewConversation() {
        var first=repository.open(1,null,"same_request","hello","test");repository.complete(first,"answer",List.of(),"test",1,1,1);
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_conversation",Integer.class);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->repository.open(1,null,"same_request","hello","test"));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_conversation",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_message",Integer.class));
    }
    @Test void failedTurnRetainsRawInputAndUsageWithoutSuccessfulAnswer() {
        var turn=begin("original");repository.fail(turn,"OUTPUT_TRUNCATED",List.of(UserMessage.from("augmented")),"test",10,4,20);
        repository.fail(turn,"late",List.of(),"test",10,4,20);
        assertEquals(1,rows());assertEquals("original",jdbc.queryForObject("SELECT content FROM t_ai_message WHERE id=?",String.class,turn.userMessageId()));
        assertEquals(0,jdbc.queryForObject("SELECT count FROM t_ai_conversation WHERE id=10",Integer.class));
        assertEquals(14,jdbc.queryForObject("SELECT total_tokens FROM t_ai_conversation WHERE id=10",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_usage_log",Integer.class));
    }
    @Test void successfulCompletionIsAtomicAndCannotDuplicate() {
        var turn=begin("raw question");var current=List.<ChatMessage>of(UserMessage.from("question with RAG"),AiMessage.from("reply"));
        repository.complete(turn,"reply",current,"test",10,5,20);
        assertThrows(ResponseStatusException.class,()->repository.complete(turn,"duplicate",current,"test",10,5,20));
        assertEquals(2,rows());assertEquals(1,jdbc.queryForObject("SELECT count FROM t_ai_conversation WHERE id=10",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT messages FROM t_ai_chat_turn WHERE id=?",String.class,turn.id()).contains("RAG"));
        assertEquals("raw question",jdbc.queryForObject("SELECT content FROM t_ai_message WHERE id=?",String.class,turn.userMessageId()));
    }
    @Test void persistenceFailureRollsBackAssistantAndLeaseRelease() {
        var turn=begin("question");jdbc.execute("DROP TABLE t_ai_usage_log");
        assertThrows(org.springframework.dao.DataAccessException.class,()->repository.complete(turn,"answer",List.of(),"test",2,1,1));
        assertEquals(1,rows());repository.requireActive(turn);assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM t_ai_chat_turn WHERE id=?",String.class,turn.id()));
    }
    @Test void oldLargeHistoryIsMarkedIncompleteAndStillSearchable() {
        history(40);var turn=begin("continue");var snapshot=repository.load(turn);
        assertTrue(snapshot.historyGap());assertTrue(snapshot.entries().size()<=43);assertEquals("问题0",repository.read(turn,1,0).getFirst().content());
    }
    @Test void historicalReferenceTriggersBoundedReadButOrdinaryInputDoesNot() {
        history(2);var turn=begin("刚才说的");var tools=new ConversationHistoryTools(repository,turn,3);clearInvocations(repository);
        assertTrue(tools.recallIfReferenced("你好").isEmpty());verify(repository,never()).search(any(),anyString(),anyLong());
        assertEquals(4,((List<?>)tools.recallIfReferenced("刚才说的").get("records")).size());
    }
}
