package com.ff.mycloud.ai.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ff.mycloud.ai.agent.action.AgentActionService;
import com.ff.mycloud.ai.agent.tool.AgentDocumentReader;
import com.ff.mycloud.ai.agent.tool.AgentTools;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.service.*;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.*;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentKnowledgeTest {
    AgentRun run; FileInfoMapperService files; AgentActionService actions; EmbeddingModel embeddings;
    EmbeddingStore<TextSegment> vectors; AgentDocumentReader reader; AgentTools tools;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        run=new AgentRun();run.setUserId(1L);run.setConversationId(10L);
        files=mock(FileInfoMapperService.class);actions=mock(AgentActionService.class);embeddings=mock(EmbeddingModel.class);
        vectors=mock(EmbeddingStore.class);reader=mock(AgentDocumentReader.class);
        tools=new AgentTools(run,files,mock(UserCenterMapperService.class),actions,null,embeddings,vectors,reader);
    }
    FileInfo file(long id,String hash) {var f=new FileInfo();f.setId(id);f.setUserId(1L);f.setFilename("report"+id);f.setFileSuffix("txt");f.setFileSize(20L);f.setIsFolder(false);f.setIsDeleted(false);f.setSha256Hash(hash);f.setUpdatedAt(LocalDateTime.of(2026,1,1,0,0));return f;}
    @Test void cleanupRequiresRealHashEvidenceAndReportsScanBoundary() {
        var a=file(1,"a".repeat(64));var b=file(2,"a".repeat(64));var c=file(3,"different-name-is-not-proof");
        when(files.list(any(QueryWrapper.class))).thenReturn(List.of(a,b,c));
        var result=tools.cleanupCandidates(0L,10L,"2026-02-01T00:00:00");
        assertEquals(3,result.get("scanned"));assertEquals(false,result.get("hasMore"));assertEquals(3L,result.get("nextAfterId"));
        assertEquals(1,((List<?>)result.get("duplicates")).size());assertEquals(3,((List<?>)result.get("oldFiles")).size());
        verifyNoInteractions(actions);
    }
    @Test void cleanupBoundsTheScanAndNeverSuggestsDeleteAutomatically() {
        List<FileInfo> rows=new ArrayList<>();for(int i=1;i<=501;i++)rows.add(file(i,null));when(files.list(any(QueryWrapper.class))).thenReturn(rows);
        var result=tools.cleanupCandidates(0L,100L,null);
        assertEquals(500,result.get("scanned"));assertEquals(true,result.get("hasMore"));assertEquals(500L,result.get("nextAfterId"));
        assertTrue(((List<?>)result.get("duplicates")).isEmpty());verifyNoInteractions(actions);
    }
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void filenameFiltersBindWildcardsAndAlwaysScopeOwner() {
        tools.searchFiles("a%_!","pdf","2026-01-01T00:00:00",null,0L,0L);
        ArgumentCaptor<QueryWrapper> query=ArgumentCaptor.forClass(QueryWrapper.class);verify(files).list(query.capture());
        String sql=query.getValue().getSqlSegment();assertTrue(sql.contains("user_id"));assertTrue(sql.contains("updated_at"));assertTrue(sql.contains("LIMIT 50"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("%a!%!_!!%"));
    }
    @Test void originalDocumentPagesAreBoundedAndCachedWithinOneRun() throws Exception {
        var f=file(7,null);when(actions.requireFile(7L,1L,false)).thenReturn(f);when(reader.read(f)).thenReturn("a".repeat(6500));
        var first=tools.readDocumentPage(7L,0);var last=tools.readDocumentPage(7L,6000);
        assertEquals(6000,first.get("nextOffset"));assertEquals(false,first.get("complete"));assertEquals(500,((String)last.get("content")).length());assertEquals(true,last.get("complete"));
        verify(reader,times(1)).read(f);assertTrue(run.getSourceFileIds().contains(7L));
    }
    @Test void unreadableDocumentIsExplicitlyUnavailable() throws Exception {
        var f=file(7,null);when(actions.requireFile(7L,1L,false)).thenReturn(f);when(reader.read(f)).thenThrow(new IllegalArgumentException("private-error"));
        var result=tools.readDocumentPage(7L,0);assertEquals(false,result.get("available"));assertEquals(false,result.get("complete"));assertFalse(result.toString().contains("private-error"));
    }
    @Test void lastPageAloneDoesNotClaimFullReadAndChangedSourceIsRejected() throws Exception {
        var f=file(7,"v1");when(actions.requireFile(7L,1L,false)).thenReturn(f);when(reader.read(f)).thenReturn("a".repeat(6500));
        var last=tools.readDocumentPage(7L,6000);
        assertEquals(true,last.get("complete"));assertEquals(false,last.get("fullyRead"));
        f.setSha256Hash("v2");
        assertThrows(AgentExecutionException.class,tools::validateSources);
    }

    @Test void chunkedDraftRejectsDuplicateOffsetsAndOnlyProposesWhenFinished() {
        var first=tools.appendDocumentDraft(null,0L,"report.md",0,"a".repeat(4000),false);
        String id=(String)first.get("draftId");
        assertThrows(IllegalArgumentException.class,()->tools.appendDocumentDraft(id,0L,"report.md",0,"duplicate",false));
        tools.appendDocumentDraft(id,0L,"report.md",4000,"b".repeat(4000),false);
        verifyNoInteractions(actions);
        var action=new com.ff.mycloud.ai.agent.action.AgentAction();action.setId("draft-action");action.setName("report.md");action.setStatus("PENDING");action.setDescription("保存草稿");
        when(actions.draft(eq(run),eq(0L),eq("report.md"),anyString())).thenReturn(action);
        tools.appendDocumentDraft(id,0L,"report.md",8000,"tail",true);
        verify(actions).draft(run,0L,"report.md","a".repeat(4000)+"b".repeat(4000)+"tail");
    }
    @Test void semanticSearchRechecksCurrentOwnershipAndDeletion() {
        when(embeddings.embed("验收")).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        var match=mockMatch(7,"allowed");var deleted=mockMatch(8,"deleted-secret");
        when(vectors.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of(match,deleted)));
        when(actions.requireFile(7L,1L,false)).thenReturn(file(7,null));
        when(actions.requireFile(8L,1L,false)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        var result=tools.searchContent("验收");assertEquals(1,((List<?>)result.get("hits")).size());assertFalse(result.toString().contains("deleted-secret"));
        var request=ArgumentCaptor.forClass(EmbeddingSearchRequest.class);verify(vectors).search(request.capture());assertNotNull(request.getValue().filter());
    }
    @SuppressWarnings("unchecked") EmbeddingMatch<TextSegment> mockMatch(long id,String text) {
        EmbeddingMatch<TextSegment> m=mock(EmbeddingMatch.class);when(m.score()).thenReturn(0.9);when(m.embedded()).thenReturn(TextSegment.from(text,Metadata.from("fileId",String.valueOf(id)).put("userId","1")));return m;
    }
    @Test void unreadSourceCannotBeCitedInGeneratedDocument() {
        when(actions.requireFile(7L,1L,false)).thenReturn(file(7,null));
        assertThrows(IllegalArgumentException.class,()->tools.draftDocument(0L,"report.md","[Evidence](file:7)"));
        verify(actions,never()).draft(any(),any(),any(),any());
    }
    @Test void clarificationPersistsQuestionWithoutAFileMutation() {
        assertEquals("WAITING_INPUT",tools.askClarification("按项目还是类型整理？").get("status"));
        assertEquals("WAITING_INPUT",run.getPauseReason());assertNotNull(run.getClarification());verifyNoInteractions(files,actions);
    }
    @Test void boundedOriginalReadNeverFetchesUnsupportedOrOversizedFile() throws Exception {
        FileService storage=mock(FileService.class);AgentDocumentReader real=new AgentDocumentReader(storage);
        var f=file(7,null);f.setFileSize(6*1024*1024L);assertThrows(IllegalArgumentException.class,()->real.read(f));verifyNoInteractions(storage);
        f.setFileSize(20L);f.setFileSuffix("exe");assertThrows(IllegalArgumentException.class,()->real.read(f));verifyNoInteractions(storage);
        f.setFileSuffix("txt");f.setPath("owned-key");when(storage.getFile("owned-key")).thenReturn(new ByteArrayInputStream("正文".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("正文",real.read(f));
    }
    @Test void duplicateLookupUsesWholeOwnerScopeRatherThanOneFilePage() {
        String hash="b".repeat(64);
        when(files.listMaps(any(QueryWrapper.class))).thenReturn(List.of(Map.of("sha256_hash",hash,"copies",25L)));
        when(files.list(any(QueryWrapper.class))).thenReturn(List.of(file(1000,hash),file(9000,hash)));
        var result=tools.findDuplicateFiles(null);assertEquals(hash,result.get("nextAfterHash"));assertEquals(false,result.get("hasMore"));
        assertTrue(result.toString().contains("copies=25"));assertTrue(result.toString().contains("membersTruncated=true"));
        verifyNoInteractions(actions);
    }
    @Test void pendingActionsCannotBeStrandedByAClarificationInTheSameModelResponse() {
        run.setPauseReason("WAITING_CONFIRMATION");tools.askClarification("另一个问题？");
        assertEquals("WAITING_CONFIRMATION",run.getPauseReason());assertNull(run.getClarification());
    }
}
