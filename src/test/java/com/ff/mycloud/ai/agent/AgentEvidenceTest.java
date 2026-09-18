package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.ai.agent.plan.AgentPlanParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentEvidenceTest {
    @Test void aRealFileIdWithInventedQuoteStillFails() {
        var run = new AgentRun();
        run.getSourceFileIds().add(7L);
        run.getTaskState().getObservations().put("obs", new AgentTaskState.Observation("obs", "readDocumentPage", "{}",
                "{\"fileId\":7,\"content\":\"合同金额为1234元\"}", true));
        var verifier = new AgentAnswerVerifier();
        String valid = "{\"status\":\"COMPLETED\",\"answer\":\"[合同](file:7)\",\"evidence\":[{\"fileId\":7,\"quote\":\"合同金额为1234元\"}]}";
        assertEquals("[合同](file:7)", verifier.verify(run, valid, Set.of(), true));
        assertThrows(AgentExecutionException.class, () -> verifier.verify(run, valid.replace("1234", "9999"), Set.of(), true));
    }

    @Test void reachingLastPageIsNotEquivalentToReadingTheWholeDocument() {
        var state = new AgentTaskState();
        state.recordPage(7L, "v1", 6000, 6500, 6500);
        assertFalse(state.getDocuments().get(7L).complete());
        state.recordPage(7L, "v1", 0, 6000, 6500);
        assertFalse(state.getDocuments().get(7L).complete());
        state.recordPage(7L, "v1", 6000, 6500, 6500);
        assertTrue(state.getDocuments().get(7L).complete());
        state.recordPage(7L, "v2", 6000, 6500, 6500);
        assertFalse(state.getDocuments().get(7L).complete());
    }

    @Test void largeToolResultStaysValidJsonAndCanBeReadWithoutLoss() throws Exception {
        var run = new AgentRun();
        String raw = new ObjectMapper().writeValueAsString(Map.of("content", "正文\n\"".repeat(5000)));
        var request = ToolExecutionRequest.builder().id("call").name("read").arguments("{}").build();
        var observation = AgentToolResults.save(run, request, raw, 100000);
        var envelope = new ObjectMapper().readTree(AgentToolResults.context(observation));
        assertTrue(envelope.path("stored").asBoolean());
        var reader = new AgentStoredResultTool(run);
        StringBuilder recovered = new StringBuilder();
        for (int offset = 0; offset < raw.length();) {
            var page = reader.readStoredResult(observation.id(), offset);
            recovered.append(page.get("content"));
            offset = (int)page.get("nextOffset");
        }
        assertEquals(raw, recovered.toString());
        assertTrue(reader.listStoredResults(0).toString().contains(observation.id()));
        assertEquals(false, reader.listStoredResults(1).get("hasMore"));
    }

    @Test void contextCompactionRetainsPinnedConstraintsAndWholeToolExchanges() {
        var run = new AgentRun();
        var context = new AgentContext(run, List.of(UserMessage.from("保留原始文件")));
        context.addExchange(List.of(UserMessage.from("old")));
        context.addExchange(List.of(UserMessage.from("new")));
        assertTrue(context.compactOldest());
        assertTrue(context.messages().toString().contains("保留原始文件"));
        assertFalse(context.messages().toString().contains("text = \"old\""));
        assertTrue(context.messages().toString().contains("new"));
        assertFalse(context.compactOldest());
    }

    @Test void historyKeepsCompleteRecentConstraintsAndFailedRuns() {
        AgentRun current = new AgentRun();
        AgentRun failed = new AgentRun();
        failed.setMessage("前文".repeat(2000) + "最后要求：绝不删除");
        failed.setStatus("FAILED");
        var actions = mock(AgentActionService.class);
        String history = AgentHistory.build(current, List.of(failed), actions).toString();
        assertTrue(history.contains("最后要求：绝不删除"));
        assertTrue(history.contains("FAILED"));
    }

    @Test void transientModelFailureRetriesOnceAndChargesTheUnknownAttempt() {
        var config = new AgentProperties();
        var store = mock(AgentRunStore.class);
        when(store.save(any())).thenReturn(true);
        var model = mock(ChatModel.class);
        var run = new AgentRun();
        run.setMessage("你好"); run.setConversationId(1L); run.setDeadline(LocalDateTime.now().plusMinutes(1));
        when(model.chat(any(ChatRequest.class))).thenThrow(new dev.langchain4j.exception.HttpException(503, "private"))
                .thenReturn(AgentEngineTest.answer("{\"steps\":[\"回答\"]}"), AgentEngineTest.answer("你好"));
        new AgentEngine(config, new AgentPlanParser(new ObjectMapper()), store).execute(run, model, List.of(), List.of(), (n,d)->{});
        assertEquals(3, run.getModelCalls());
        assertFalse(run.isUsageComplete());
        assertTrue(run.getBudgetTokens() > run.totalTokens());
    }
}
