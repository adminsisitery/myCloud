package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.agent.plan.AgentPlanParser;
import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentEngineTest {
    AgentProperties config = new AgentProperties();
    AgentRunStore store = mock(AgentRunStore.class);
    ChatModel model = mock(ChatModel.class);
    AgentPlanParser parser = new AgentPlanParser(new ObjectMapper());
    AgentRun run;
    AgentEngine engine;
    @BeforeEach void setup() {
        run = new AgentRun(); run.setUserId(1L); run.setConversationId(10L); run.setMessage("列出文件"); run.setDeadline(LocalDateTime.now().plusMinutes(5));
        when(store.save(any())).thenReturn(true);
        engine = new AgentEngine(config, parser, store);
    }
    public static class TestTools {
        int calls;
        @Tool("list files") public String listFiles() { calls++; return "report.txt, id=7"; }
    }
    public static class BrokenTool { @Tool("broken") public String broken() { throw new IllegalStateException("private-password"); } }
    public static class PausingTool {
        final AgentRun run; PausingTool(AgentRun run) {this.run=run;}
        @Tool("ask") public String ask() {run.setClarification("需要哪个目录？");run.setPauseReason("WAITING_INPUT");return "waiting";}
    }
    @Test void clarificationStopsBeforeDependentStepsAndPreservesPlan() {
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"确认目录\",\"整理文件\"]}"),tool("ask"));
        engine.execute(run,model,List.of(new PausingTool(run)),List.of(),(n,d)->{});
        assertEquals(2,run.getModelCalls());assertEquals("WAITING_INPUT",run.getPauseReason());assertEquals("WAITING",run.getPlan().getSteps().getFirst().getStatus());
        assertEquals("PENDING",run.getPlan().getSteps().get(1).getStatus());assertTrue(run.getOutput().contains("需要哪个目录"));
    }
    @Test void continuationCannotResetTotalTokenBudget() {
        run.setPriorTokens(config.getMaxTotalTokens());
        assertThrows(IllegalStateException.class,()->engine.execute(run,model,List.of(),List.of(),(n,d)->{}));verifyNoInteractions(model);
    }
    static ChatResponse answer(String text) { return ChatResponse.builder().aiMessage(AiMessage.from(text)).tokenUsage(new TokenUsage(10,5)).build(); }
    static ChatResponse tool(String name) {
        return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder().id("call-1").name(name).arguments("{}").build())).tokenUsage(new TokenUsage(10,5)).build();
    }
    @Test void plansExecutesToolsAndAccountsForEveryModelResponse() {
        TestTools tools=new TestTools();
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"列出文件\"]}"),tool("listFiles"),answer("report.txt"));
        List<String> events=new ArrayList<>();
        engine.execute(run,model,List.of(tools),List.of(),(name,data)->events.add(name));
        assertEquals(1,tools.calls); assertEquals(3,run.getModelCalls()); assertEquals(30,run.getInputTokens()); assertEquals(15,run.getOutputTokens());
        assertEquals(1,run.getToolCalls()); assertEquals("DONE",run.getPlan().getSteps().getFirst().getStatus());
        assertEquals("report.txt",run.getOutput());
        assertEquals(List.of("agent_status","agent_plan","agent_step_start","agent_tool_start","agent_tool_done","chunk","agent_step_done"),events);
    }
    @Test void stopsAnInfiniteToolLoopAtExactBudget() {
        config.setMaxToolCalls(2); TestTools tools=new TestTools();
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"列出\"]}"),tool("listFiles"));
        assertThrows(IllegalStateException.class,()->engine.execute(run,model,List.of(tools),List.of(),(n,d)->{}));
        assertEquals(2,tools.calls); assertEquals(2,run.getToolCalls());
    }
    @Test void cancellationAfterModelResponsePreventsToolExecution() {
        TestTools tools=new TestTools();
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"列出\"]}"));
        when(model.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            if (run.getModelCalls()==1) return answer("{\"steps\":[\"列出\"]}");
            when(store.shouldStop(run.getId())).thenReturn(true); return tool("listFiles");
        });
        assertThrows(CancellationException.class,()->engine.execute(run,model,List.of(tools),List.of(),(n,d)->{}));
        assertEquals(0,tools.calls);
    }
    @Test void expiredRunDoesNotContactModel() {
        run.setDeadline(LocalDateTime.now().minusSeconds(1));
        assertThrows(CancellationException.class,()->engine.execute(run,model,List.of(),List.of(),(n,d)->{}));
        verifyNoInteractions(model);
    }
    @Test void absentProviderUsageIsExplicitlyMarkedIncomplete() {
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("{\"steps\":[\"回答\"]}")).build(),answer("好"));
        engine.execute(run,model,List.of(),List.of(),(n,d)->{});
        assertFalse(run.isUsageComplete()); assertEquals(15,run.totalTokens());
    }
    @Test void toolExceptionDoesNotExposeInternalError() {
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"调用\"]}"),tool("broken"),answer("操作失败"));
        List<String> events=new ArrayList<>();
        assertThrows(AgentExecutionException.class, () -> engine.execute(run,model,List.of(new BrokenTool()),List.of(),(n,d)->events.add(d.toString())));
        assertTrue(events.stream().anyMatch(s->s.contains("工具执行失败")));
        assertTrue(events.stream().noneMatch(s->s.contains("private-password")));
    }
    @Test void modelCallBudgetIncludesPlanning() {
        config.setMaxModelCalls(1);
        when(model.chat(any(ChatRequest.class))).thenReturn(answer("{\"steps\":[\"回答\"]}"));
        assertThrows(IllegalStateException.class,()->engine.execute(run,model,List.of(),List.of(),(n,d)->{}));
        verify(model,times(1)).chat(any(ChatRequest.class));
    }
    @Test void rejectsOversizedContextBeforeModelCall() {
        config.setMaxContextChars(5);
        assertThrows(IllegalStateException.class,()->engine.execute(run,model,List.of(),List.of(),(n,d)->{})); verifyNoInteractions(model);
    }
    @ParameterizedTest
    @ValueSource(strings={"{}","{\"steps\":[]}","{\"steps\":[null]}","{\"steps\":[\"\"]}","nonsense","{\"steps\":[{}]}","{\"steps\":[1]}"})
    void malformedPlansFallbackWithoutLosingOriginalGoal(String json) {
        var plan=parser.parse(json,"original goal",6);
        assertEquals("original goal",plan.getGoal()); assertEquals("original goal",plan.getSteps().getFirst().getDescription()); assertNotNull(plan.getPlanId());
    }
    @Test void supportsBothExistingPlanFormats() {
        var plan=parser.parse("```json\n{\"steps\":[\"搜索\",{\"description\":\"总结\",\"status\":\"DONE\"}]}\n```","goal",6);
        assertEquals(2,plan.getSteps().size()); assertEquals(1,plan.getSteps().get(1).getIndex()); assertEquals("PENDING",plan.getSteps().get(1).getStatus());
    }
    @Test void doesNotSilentlyTruncateExcessSteps() {
        assertEquals("goal",parser.parse("{\"steps\":[\"a\",\"b\"]}","goal",1).getSteps().getFirst().getDescription());
    }
}
