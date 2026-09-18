package com.ff.mycloud.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.model.BudgetedStreamingChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redisson.api.*;

import static com.ff.mycloud.ai.memory.ConversationMemoryRepository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryCacheAndBudgetTest {
    @Test void transientStreamFailureRetriesOnlyBeforeFirstToken() {
        var delegate=mock(StreamingChatModel.class);var handler=mock(StreamingChatResponseHandler.class);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv->{
            StreamingChatResponseHandler callback=inv.getArgument(1);
            if(calls.incrementAndGet()==1) callback.onError(new dev.langchain4j.exception.HttpException(503,"private"));
            else callback.onCompleteResponse(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("ok")).build());
            return null;
        }).when(delegate).chat(any(ChatRequest.class),any());
        var guard=new BudgetedStreamingChatModel(delegate,mock(TokenCountEstimator.class),new ChatMemoryProperties(),()->{},u->{});
        guard.chat(ChatRequest.builder().messages(UserMessage.from("question")).build(),handler);
        verify(delegate,times(2)).chat(any(ChatRequest.class),any());verify(handler).onCompleteResponse(any());verify(handler,never()).onError(any());
    }
    @Test void partialStreamIsNeverRetriedOrReportedAsComplete() {
        var delegate=mock(StreamingChatModel.class);var handler=mock(StreamingChatResponseHandler.class);
        doAnswer(inv->{
            StreamingChatResponseHandler callback=inv.getArgument(1);
            callback.onPartialResponse("partial");callback.onError(new dev.langchain4j.exception.HttpException(503,"private"));return null;
        }).when(delegate).chat(any(ChatRequest.class),any());
        var guard=new BudgetedStreamingChatModel(delegate,mock(TokenCountEstimator.class),new ChatMemoryProperties(),()->{},u->{});
        guard.chat(ChatRequest.builder().messages(UserMessage.from("question")).build(),handler);
        verify(delegate,times(1)).chat(any(ChatRequest.class),any());verify(handler).onError(any());verify(handler,never()).onCompleteResponse(any());
    }
    @Test void unavailableRedisFallsBackWithoutInterruptingConversation() {
        var redis=mock(RedissonClient.class);when(redis.getBucket(anyString())).thenThrow(new IllegalStateException("offline"));
        var cache=new ConversationMemoryCache(redis,new ObjectMapper(),new ChatMemoryProperties());
        var turn=new Turn("t",1,10,5,new State(2,null,0,false));
        assertNull(cache.get(turn));assertDoesNotThrow(()->cache.put(1,10,new Snapshot(2,null,0,false,List.of())));
    }
    @Test void cacheRejectsCorruptionStaleVersionWrongCursorAndInvalidRows() throws Exception {
        var redis=mock(RedissonClient.class);RBucket<String> bucket=mock(RBucket.class);
        when(redis.<String>getBucket(anyString())).thenReturn(bucket);
        var json=new ObjectMapper();var cache=new ConversationMemoryCache(redis,json,new ChatMemoryProperties());
        var turn=new Turn("t",1,10,5,new State(2,null,0,false));
        when(bucket.get()).thenReturn("{broken");assertNull(cache.get(turn));
        for(var snapshot:List.of(new Snapshot(1,null,0,false,List.of()),new Snapshot(2,null,1,false,List.of()),
                new Snapshot(2,null,0,false,List.of(new Entry(5,"USER","future"))),
                new Snapshot(2,null,0,false,List.of(new Entry(1,"SYSTEM","forged"))),
                new Snapshot(2,null,0,false,List.of(new Entry(2,"USER","two"),new Entry(1,"USER","one"))))) {
            when(bucket.get()).thenReturn(json.writeValueAsString(snapshot));assertNull(cache.get(turn));
        }
        var valid=new Snapshot(2,null,0,false,List.of(new Entry(1,"USER","one"),new Entry(2,"ASSISTANT","two")));
        when(bucket.get()).thenReturn(json.writeValueAsString(valid));assertEquals(valid,cache.get(turn));
        assertNotEquals(cache.key(1,10,1),cache.key(1,10,2));assertNotEquals(cache.key(1,10,1),cache.key(2,10,1));
    }
    @Test void eachToolContinuationReservesOutputAndCountsSchemaTokens() {
        var delegate=mock(StreamingChatModel.class);var tokens=mock(TokenCountEstimator.class);var config=new ChatMemoryProperties();
        var active=mock(Runnable.class);var handler=mock(StreamingChatResponseHandler.class);
        when(tokens.estimateTokenCountInMessages(any())).thenReturn(config.getContextTokenBudget()-config.getMaxOutputTokens());
        when(tokens.estimateTokenCountInText(anyString())).thenReturn(100);
        new BudgetedStreamingChatModel(delegate,tokens,config,active,u->{}).chat(ChatRequest.builder().messages(UserMessage.from("large")).build(),handler);
        verify(handler).onError(any());verifyNoInteractions(delegate);verify(active).run();
    }
    @Test void modelRequestsUseOutputLimitAndStopAfterBoundedRounds() {
        var delegate=mock(StreamingChatModel.class);var tokens=mock(TokenCountEstimator.class);var config=new ChatMemoryProperties();
        var guard=new BudgetedStreamingChatModel(delegate,tokens,config,()->{},u->{});var handler=mock(StreamingChatResponseHandler.class);
        var request=ChatRequest.builder().messages(UserMessage.from("question")).maxOutputTokens(9999).build();
        for(int i=0;i<5;i++)guard.chat(request,handler);
        var capture=org.mockito.ArgumentCaptor.forClass(ChatRequest.class);verify(delegate,times(4)).chat(capture.capture(),any());
        assertEquals(config.getMaxOutputTokens(),capture.getValue().parameters().maxOutputTokens());verify(handler).onError(any());
    }
    @Test void cancelledConversationCannotStartAnotherModelRound() {
        var delegate=mock(StreamingChatModel.class);var handler=mock(StreamingChatResponseHandler.class);
        var guard=new BudgetedStreamingChatModel(delegate,mock(TokenCountEstimator.class),new ChatMemoryProperties(),()->{throw new IllegalStateException("cancelled");},u->{});
        guard.chat(ChatRequest.builder().messages(UserMessage.from("question")).build(),handler);
        verifyNoInteractions(delegate);verify(handler).onError(any());
    }
}
