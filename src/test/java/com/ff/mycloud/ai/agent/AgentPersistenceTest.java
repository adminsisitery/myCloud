package com.ff.mycloud.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ff.mycloud.ai.agent.action.AgentAction;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.ai.agent.tool.AgentToolFactory;
import com.ff.mycloud.common.util.UserContext;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AgentPersistenceTest.Config.class)
class AgentPersistenceTest {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource source() { return new DriverManagerDataSource("jdbc:h2:mem:agent;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""); }
        @Bean JdbcTemplate jdbc(DataSource s) { return new JdbcTemplate(s); }
        @Bean DataSourceTransactionManager tx(DataSource s) { return new DataSourceTransactionManager(s); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AgentRunStore store(JdbcTemplate j,ObjectMapper o) { return new AgentRunStore(j,o); }
    }
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired AgentRunStore store;
    @org.springframework.beans.factory.annotation.Autowired ObjectMapper json;
    AgentActionService actions;
    FileInfoMapperService files;
    FileService fileService;
    FolderService folders;
    DeleteFileService trash;
    UserCenterMapperService users;
    AgentRun run;
    FileInfo file;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        String sql=Files.readString(Path.of("deploy/001-agent.sql")).split("-- Idempotent upgrade")[0];
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(jdbc.getDataSource());
        jdbc.update("INSERT INTO t_ai_conversation(id,user_id,title,mode) VALUES(10,1,'Agent','AGENT')");
        files=mock(FileInfoMapperService.class); fileService=mock(FileService.class); folders=mock(FolderService.class); trash=mock(DeleteFileService.class);
        RedissonClient redis=mock(RedissonClient.class); RLock lock=mock(RLock.class);
        when(redis.getLock(anyString())).thenReturn(lock); when(lock.tryLock()).thenReturn(true);
        users=mock(UserCenterMapperService.class);
        actions=new AgentActionService(jdbc,json,files,fileService,folders,trash,new AgentProperties(),store,redis,users);
        run=new AgentRun();run.setUserId(1L);run.setConversationId(10L);run.setMessage("重命名报告");run.setModel("test");run.setDeadline(LocalDateTime.now().plusMinutes(5)); store.create(run);
        file=new FileInfo();file.setId(7L);file.setUserId(1L);file.setFilename("report");file.setFileSuffix("txt");file.setIsFolder(false);file.setIsDeleted(false);file.setParentId(0L);file.setLocalFilePath("report.txt");file.setUpdatedAt(LocalDateTime.of(2026,1,1,0,0));
        when(files.getById(7L)).thenReturn(file);
    }
    private AgentAction propose() { return actions.propose(run,AgentAction.Operation.RENAME_FILE,7L,0L,"new-report"); }
    @Test void durableEvidenceSurvivesReloadButIsExcludedFromPublicSnapshots() throws Exception {
        run.setStatus("RUNNING");
        run.getTaskState().getInstructions().add("最终澄清：保留原始文件");
        run.getTaskState().getObservations().put("obs1", new AgentTaskState.Observation("obs1", "readDocumentPage", "{}", "private-body", true));
        run.getTaskState().recordPage(7L, "v1", 0, 6, 6);
        run.setBudgetTokens(1234);
        assertTrue(store.save(run));
        AgentRun restored = store.get(run.getId(), 1L);
        assertEquals("private-body", restored.getTaskState().getObservations().get("obs1").result());
        assertTrue(restored.getTaskState().getDocuments().get(7L).complete());
        assertEquals(List.of("最终澄清：保留原始文件"), restored.getTaskState().getInstructions());
        assertEquals(1234, restored.getBudgetTokens());
        assertFalse(json.writeValueAsString(restored).contains("private-body"));
        assertFalse(json.writeValueAsString(restored).contains("taskState"));
    }

    @Test void historyRecallAlwaysScopesOwnerAndConversation() {
        assertTrue(store.history(10L, 2L, 0).isEmpty());
        assertTrue(store.history(11L, 1L, 0).isEmpty());
        assertEquals(1, store.history(10L, 1L, 0).size());
        assertTrue(store.history(10L, 1L, 20).isEmpty());
    }
    private void finish() { run.setStatus("WAITING_CONFIRMATION");run.setOutput("请确认修改");store.finish(run); }
    @Test void duplicateActiveUserAndConversationAreRejectedByDatabase() {
        jdbc.update("INSERT INTO t_ai_conversation(id,user_id,title,mode) VALUES(11,1,'Other','AGENT')");
        AgentRun other=new AgentRun();other.setUserId(1L);other.setConversationId(11L);other.setModel("test");other.setDeadline(run.getDeadline());
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->store.create(other));
        other.setUserId(2L);other.setConversationId(10L);
        assertThrows(ResponseStatusException.class,()->store.create(other));
    }
    @Test void historyAndTaskReadsAreUserScoped() {
        assertThrows(ResponseStatusException.class,()->store.get(run.getId(),2L));
        assertTrue(store.history(10L,2L).isEmpty());
        assertEquals(run.getMessage(),store.get(run.getId(),1L).getMessage());
    }
    @Test void proposalDoesNotPerformWriteAndSameOperationIsDeduplicated() {
        var first=propose();var second=propose();
        assertEquals(first.getId(),second.getId());verifyNoInteractions(fileService,folders,trash);
        assertEquals(1,actions.list(run.getId(),1L).size());
    }
    @Test void confirmationRequiresTheOwner() {
        var action=propose();finish();
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(2L);
            assertThrows(ResponseStatusException.class,()->actions.confirm(action.getId(),2L));
        }
        verifyNoInteractions(fileService);
    }
    @Test void repeatedConfirmationExecutesExactlyOnce() {
        var action=propose();finish();
        doAnswer(inv->{file.setFilename("new-report");return null;}).when(fileService).renameFile(7L,"new-report");
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("SUCCEEDED",actions.confirm(action.getId(),1L).getStatus());
            assertEquals("SUCCEEDED",actions.confirm(action.getId(),1L).getStatus());
        }
        verify(fileService,times(1)).renameFile(7L,"new-report");
    }
    @Test void changedFileCannotBeConfirmed() {
        var action=propose();finish();file.setFilename("different");
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertThrows(ResponseStatusException.class,()->actions.confirm(action.getId(),1L));
        }
        assertEquals("STALE",actions.get(action.getId(),1L).getStatus());verifyNoInteractions(fileService);
    }
    @Test void failedSideEffectIsUnknownAndNeverRetried() {
        var action=propose();finish();doThrow(new IllegalStateException("simulated MQ failure")).when(fileService).renameFile(anyLong(),anyString());
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("UNKNOWN",actions.confirm(action.getId(),1L).getStatus());
            assertEquals("UNKNOWN",actions.confirm(action.getId(),1L).getStatus());
        }
        verify(fileService,times(1)).renameFile(anyLong(),anyString());
    }
    @Test void crossUserParentAndDeletedParentAreRejected() {
        file.setIsFolder(true);file.setUserId(2L);
        assertThrows(ResponseStatusException.class,()->actions.propose(run,AgentAction.Operation.CREATE_FOLDER,null,7L,"child"));
        file.setUserId(1L);file.setIsDeleted(true);
        assertThrows(ResponseStatusException.class,()->actions.propose(run,AgentAction.Operation.CREATE_FOLDER,null,7L,"child"));
    }
    @Test void cancellationInvalidatesPendingActionsAndWinsFinishRace() {
        var action=propose();store.cancel(run.getId(),1L);finish();
        assertEquals("CANCELLED",store.get(run.getId(),1L).getStatus());
        assertEquals("REJECTED",actions.get(action.getId(),1L).getStatus());
    }
    @Test void rejectedActionCannotBeConfirmed() {
        var action=propose();finish();actions.reject(action.getId(),1L);
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("REJECTED",actions.confirm(action.getId(),1L).getStatus());
        }
        verifyNoInteractions(fileService);
    }
    @Test void finalizationIsAtomicAndDoesNotDuplicateMessagesOrUsage() {
        run.setInputTokens(20);run.setOutputTokens(10);finish();store.finish(run);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_message",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_usage_log",Integer.class));
        assertEquals(30,jdbc.queryForObject("SELECT total_tokens FROM t_ai_conversation WHERE id=10",Integer.class));
        assertNull(jdbc.queryForObject("SELECT active_user FROM t_ai_agent_run WHERE id=?",Long.class,run.getId()));
    }
    @Test void persistenceFailureRollsBackTerminalStateAndMessages() {
        jdbc.execute("DROP TABLE t_ai_usage_log");
        assertThrows(org.springframework.dao.DataAccessException.class,this::finish);
        assertEquals("QUEUED",store.get(run.getId(),1L).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_message",Integer.class));
    }
    @Test void uncompletedRunCannotExecuteProposals() {
        var action=propose();
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertThrows(ResponseStatusException.class,()->actions.confirm(action.getId(),1L));
        }
        verifyNoInteractions(fileService);
    }

    @Test void requestIdIsScopedToUserAndCannotCreateDuplicateRun() {
        assertEquals(run.getId(),store.findRequest(run.getRequestId(),1L).orElseThrow().getId());
        assertTrue(store.findRequest(run.getRequestId(),2L).isEmpty());
        finish();
        AgentRun duplicate=new AgentRun();duplicate.setRequestId(run.getRequestId());duplicate.setUserId(1L);duplicate.setConversationId(10L);duplicate.setModel("test");duplicate.setDeadline(run.getDeadline());
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->store.create(duplicate));
    }

    @Test void expiredActionNeverExecutes() throws Exception {
        var action=propose();finish();action.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        jdbc.update("UPDATE t_ai_agent_action SET snapshot=?,expires_at=? WHERE id=?",json.writeValueAsString(action),action.getExpiresAt(),action.getId());
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("EXPIRED",actions.confirm(action.getId(),1L).getStatus());
        }
        verifyNoInteractions(fileService);
    }

    @Test void timeoutFinalizationPreservesKnownUsageExactlyOnce() {
        jdbc.update("UPDATE t_ai_agent_run SET status='TIMED_OUT',active_user=NULL,active_conversation=NULL WHERE id=?",run.getId());
        run.setStatus("TIMED_OUT");run.setInputTokens(12);run.setOutputTokens(4);store.finish(run);store.finish(run);
        assertEquals(16,store.get(run.getId(),1L).totalTokens());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM t_ai_usage_log",Integer.class));
    }

    @Test void returningWithoutChangingFileIsNotSuccess() {
        var a=propose();finish();
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("UNKNOWN",actions.confirm(a.getId(),1L).getStatus());
        }
        assertFalse(actions.get(a.getId(),1L).isVerified());
    }

    @Test void reconcileOnlyReadsAndDoesNotRepeatSideEffect() {
        var a=propose();finish();jdbc.update("UPDATE t_ai_agent_action SET status='UNKNOWN' WHERE id=?",a.getId());
        file.setFilename("new-report");
        var reconciled=actions.reconcile(a.getId(),1L);
        assertEquals("SUCCEEDED",reconciled.getStatus());assertTrue(reconciled.isVerified());assertEquals(7L,reconciled.getResultFileId());
        verifyNoInteractions(fileService);
    }

    @Test void undoUsesRecordedBeforeStateAndRequiresAnotherConfirmation() {
        var a=propose();finish();doAnswer(inv->{file.setFilename("new-report");return null;}).when(fileService).renameFile(7L,"new-report");
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);actions.confirm(a.getId(),1L);
        }
        AgentRun next=newRun();store.create(next);
        var undo=actions.proposeUndo(next,a.getId());
        assertEquals("report.txt",undo.getName());assertEquals("PENDING",undo.getStatus());assertEquals(a.getId(),undo.getReversesActionId());
        verify(fileService,times(1)).renameFile(anyLong(),anyString());
        file.setUpdatedAt(LocalDateTime.now());
        assertThrows(ResponseStatusException.class,()->actions.proposeUndo(next,a.getId()));
    }

    @Test void continuationCannotRepeatRejectedOperation() {
        var a=propose();finish();actions.reject(a.getId(),1L);
        AgentRun next=newRun();next.setParentRunId(run.getId());store.create(next);
        assertThrows(ResponseStatusException.class,()->actions.propose(next,AgentAction.Operation.RENAME_FILE,7L,0L,"new-report"));
    }

    @Test void draftIsDownloadableDataAndNeverUploadsBeforeConfirmation() {
        var a=actions.draft(run,0L,"summary.md","# Summary\nEvidence");
        assertEquals("# Summary\nEvidence",actions.get(a.getId(),1L).getContent());
        assertEquals("WAITING_CONFIRMATION",run.getPauseReason());verifyNoInteractions(fileService,users);
        assertThrows(ResponseStatusException.class,()->actions.get(a.getId(),2L));
        assertThrows(ResponseStatusException.class,()->actions.draft(run,0L,"bad.html","<script>"));
        assertThrows(ResponseStatusException.class,()->actions.draft(run,0L,"bad.csv","name,value\nexample,=1+1"));
    }

    @Test void insufficientQuotaNeverUploads() {
        var a=actions.draft(run,0L,"summary.md","hello");finish();
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertEquals("FAILED",actions.confirm(a.getId(),1L).getStatus());
        }
        verifyNoInteractions(fileService);verify(users).reserveSpace(1L,5L);
    }

    @Test void savedArtifactIsVerifiedByContentHashAndOnlyReservesOnce() throws Exception {
        var a=actions.draft(run,0L,"summary.md","hello");finish();
        when(users.reserveSpace(1L,5)).thenReturn(true);
        var created=new FileInfo();created.setId(8L);created.setUserId(1L);created.setIsDeleted(false);created.setFilename("summary");created.setFileSuffix("md");created.setParentId(0L);
        created.setSha256Hash(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        when(files.getById(8L)).thenReturn(created);
        when(fileService.uploadFile(any(),isNull())).thenAnswer(inv->{when(files.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(created));return "object-key";});
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            var saved=actions.confirm(a.getId(),1L);assertEquals("SUCCEEDED",saved.getStatus());assertEquals(8L,saved.getResultFileId());assertTrue(saved.isVerified());
            actions.confirm(a.getId(),1L);
        }
        verify(users,times(1)).reserveSpace(1L,5L);verify(fileService,times(1)).uploadFile(any(),isNull());
    }

    @Test void waitingInputIsDurableAndCanReleaseExecutionSlot() {
        run.setClarification("按项目还是类型整理？");run.setStatus("WAITING_INPUT");store.finish(run);
        assertEquals("WAITING_INPUT",store.get(run.getId(),1L).getStatus());
        assertEquals(run.getClarification(),store.get(run.getId(),1L).getClarification());
        store.create(newRun());
    }

    @Test void auditDoesNotLeakAcrossUsersOrConversations() {
        propose();assertEquals(1,actions.audit(1L,10L,0).size());
        assertTrue(actions.audit(2L,10L,0).isEmpty());assertTrue(actions.audit(1L,11L,0).isEmpty());
    }

    @Test void continuationPreservesGoalAndReplaysSameAttempt() throws Exception {
        run.setGoal("创建目录再移动资料");run.setStatus("WAITING_INPUT");run.setClarification("目录名？");store.finish(run);
        var models=mock(com.ff.mycloud.ai.model.ModelRegistry.class);when(models.requireModelName(any())).thenReturn("test");
        var conv=mock(com.ff.mycloud.ai.persistence.mapper.AiConversationMapper.class);
        var conversation=new com.ff.mycloud.ai.persistence.entity.AiConversation();conversation.setId(10L);conversation.setUserId(1L);conversation.setMode("AGENT");when(conv.selectById(10L)).thenReturn(conversation);
        var engine=mock(AgentEngine.class);var tools=mock(AgentToolFactory.class);when(tools.create(any(),anyBoolean())).thenReturn(List.of());
        var orchestrator=new AgentOrchestrator(models,conv,store,new AgentProperties(),engine,tools,actions,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try(var context=mockStatic(UserContext.class)) {
            context.when(UserContext::getCurrentUserId).thenReturn(1L);
            assertThrows(ResponseStatusException.class,()->orchestrator.resume(run.getId(),""));
            orchestrator.resume(run.getId(),"工作资料");
            AgentRun child=null;
            for(int i=0;i<100;i++) {child=store.findRequest("resume_"+run.getId(),1L).orElse(null);if(child!=null && "SUCCEEDED".equals(child.getStatus()))break;Thread.sleep(20);}
            assertNotNull(child);assertEquals("SUCCEEDED",child.getStatus());assertEquals(run.getId(),child.getParentRunId());assertEquals(run.getGoal(),child.getGoal());
            orchestrator.resume(run.getId(),"工作资料");
            verify(engine,times(1)).execute(any(),any(),anyList(),anyList(),any());
        } finally {orchestrator.close();}
    }

    private AgentRun newRun() {
        AgentRun next=new AgentRun();next.setUserId(1L);next.setConversationId(10L);next.setMessage("继续");next.setModel("test");next.setDeadline(LocalDateTime.now().plusMinutes(5));return next;
    }
}
