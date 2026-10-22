package com.luutry.agent.web;

import com.luutry.agent.common.model.dto.ChatRequest;
import com.luutry.agent.common.model.vo.ApiResponse;
import com.luutry.agent.common.model.vo.ChatResponse;
import com.luutry.agent.aggregate.ContextAggregator;
import com.luutry.agent.aggregate.DefaultContextAggregator;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.critique.SelfCritiqueService;
import com.luutry.agent.critique.model.CritiqueResult;
import com.luutry.agent.critique.model.CritiqueVerdict;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.planner.PlannerService;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.preprocessor.cleaner.CleanResult;
import com.luutry.agent.preprocessor.cleaner.TruncationBoundary;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.preprocessor.router.QueryRouter;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentController.
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private QueryRouter queryRouter;

    @Mock
    private PlannerService plannerService;

    @Mock
    private SelfCritiqueService selfCritiqueService;

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private UserDirectory userDirectory;

    private ContextAggregator contextAggregator;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        contextAggregator = new DefaultContextAggregator();
        controller = new AgentController(queryRouter, plannerService, contextAggregator,
                selfCritiqueService, memoryManager, userDirectory);
        lenient().when(userDirectory.resolve(anyString())).thenReturn(
                UserContext.builder().userId("user1").managedOrgIds(List.of("DEPT_TECH")).build());
    }

    @Test
    void chat_validRequest_returnsSuccessResponse() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("查看技术部人员");
        processedQuery.setIntent(QueryIntent.TOOL);
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);

        AgentResponse agentResponse = AgentResponse.success("技术部有15人", "task1", 1);
        when(plannerService.plan(any(), anyString(), anyString())).thenReturn(agentResponse);

        // Mock session context for conversation turns
        MemoryManager.SessionContext emptySessionCtx = new MemoryManager.SessionContext("", Collections.emptyList(), null);
        when(memoryManager.getSessionContext(anyString())).thenReturn(emptySessionCtx);

        CritiqueResult critique = CritiqueResult.pass();
        when(selfCritiqueService.critique(any(), any(), any())).thenReturn(critique);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("技术部有15人", result.getData().getAnswer());
        assertEquals(CritiqueVerdict.PASS, result.getData().getVerdict());
    }

    @Test
    void chat_invalidRequest_returnsBadRequest() {
        ChatRequest request = ChatRequest.builder()
                .query("")
                .userId("user1")
                .sessionId("session1")
                .build();

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
    }

    @Test
    void chat_nullRequest_returnsBadRequest() {
        ApiResponse<ChatResponse> result = controller.chat(null);

        assertFalse(result.isSuccess());
    }

    @Test
    void chat_critiqueReject_usesRevisedAnswer() {
        ChatRequest request = ChatRequest.builder()
                .query("查看绩效")
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("查看绩效");
        processedQuery.setIntent(QueryIntent.TOOL);
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);

        AgentResponse agentResponse = AgentResponse.success("编造的数据100%", "task1", 1);
        when(plannerService.plan(any(), anyString(), anyString())).thenReturn(agentResponse);

        // Mock session context
        MemoryManager.SessionContext emptySessionCtx = new MemoryManager.SessionContext("", Collections.emptyList(), null);
        when(memoryManager.getSessionContext(anyString())).thenReturn(emptySessionCtx);

        CritiqueResult critique = CritiqueResult.reject("hallucination", "抱歉，信息不足");
        when(selfCritiqueService.critique(any(), any(), any())).thenReturn(critique);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess());
        assertEquals("抱歉，信息不足", result.getData().getAnswer());
        assertEquals(CritiqueVerdict.REJECT, result.getData().getVerdict());
    }

    @Test
    void chat_exceptionThrown_returnsErrorResponse() {
        ChatRequest request = ChatRequest.builder()
                .query("test")
                .userId("user1")
                .sessionId("session1")
                .build();

        when(queryRouter.route(anyString(), any())).thenThrow(new RuntimeException("internal error"));

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
    }

    @Test
    void chat_withHistory_passesHistoryToRouter() {
        ChatRequest request = ChatRequest.builder()
                .query("他们的绩效")
                .userId("user1")
                .sessionId("session1")
                .history(List.of("查看技术部人员"))
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("他们的绩效");
        processedQuery.setIntent(QueryIntent.TOOL);
        when(queryRouter.route(eq("他们的绩效"), eq(List.of("查看技术部人员")))).thenReturn(processedQuery);

        AgentResponse agentResponse = AgentResponse.success("技术部绩效均为B级以上", "task1", 2);
        when(plannerService.plan(any(), anyString(), anyString())).thenReturn(agentResponse);

        // Mock session context
        MemoryManager.SessionContext emptySessionCtx = new MemoryManager.SessionContext("", Collections.emptyList(), null);
        when(memoryManager.getSessionContext(anyString())).thenReturn(emptySessionCtx);

        CritiqueResult critique = CritiqueResult.pass();
        when(selfCritiqueService.critique(any(), any(), any())).thenReturn(critique);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess());
        verify(queryRouter).route("他们的绩效", List.of("查看技术部人员"));
    }

    @Test
    void chat_memoryHistory_preferredAndReversed() {
        ChatRequest request = ChatRequest.builder()
                .query("他们的绩效")
                .userId("user1")
                .sessionId("session1")
                .history(List.of("客户端伪造的一轮"))
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("他们的绩效");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);

        when(memoryManager.getRecentTurns("session1", 3)).thenReturn(List.of(
                ConversationTurn.builder().turnNumber(1).userMessage("第一轮").build(),
                ConversationTurn.builder().turnNumber(2).userMessage("第二轮").build()));

        controller.chat(request);

        verify(queryRouter).route(eq("他们的绩效"), eq(List.of("第二轮", "第一轮")));
    }

    @Test
    void chat_clientHistory_normalizedToNewestFirst() {
        ChatRequest request = ChatRequest.builder()
                .query("他们的绩效")
                .userId("user1")
                .sessionId("session1")
                .history(List.of("最旧", "中间", "最新"))
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("他们的绩效");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);

        controller.chat(request);

        verify(queryRouter).route(eq("他们的绩效"), eq(List.of("最新", "中间", "最旧")));
    }

    @Test
    void chat_success_writesSessionMemoryWithCleanedQuery() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("查看技术部人员");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);
        when(memoryManager.getSessionTurnCount("session1")).thenReturn(2);

        controller.chat(request);

        ArgumentCaptor<ConversationTurn> captor = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(memoryManager).addToSession(eq("session1"), eq("user1"), captor.capture());
        assertEquals(3, captor.getValue().getTurnNumber());
        assertEquals("查看技术部人员", captor.getValue().getUserMessage());
        assertEquals("答案", captor.getValue().getAssistantMessage());
    }

    @Test
    void chat_hardRejectedQuery_notWrittenToMemory() {
        String longQuery = "查".repeat(2500);
        ChatRequest request = ChatRequest.builder()
                .query(longQuery)
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery(longQuery);
        processedQuery.setIntent(QueryIntent.TOOL);
        processedQuery.setCleanResult(CleanResult.builder()
                .cleanedText("查".repeat(2000))
                .truncated(true)
                .hardRejected(true)
                .boundary(TruncationBoundary.CODE_POINT)
                .originalCodePoints(2500)
                .build());
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        verify(memoryManager, never()).addToSession(any(), any(), any());
    }

    private void stubSuccessfulPipeline(ProcessedQuery processedQuery) {
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);
        when(plannerService.plan(any(), anyString(), anyString()))
                .thenReturn(AgentResponse.success("答案", "task1", 1));
        when(memoryManager.getSessionContext(anyString()))
                .thenReturn(new MemoryManager.SessionContext("", Collections.emptyList(), null));
        when(selfCritiqueService.critique(any(), any(), any())).thenReturn(CritiqueResult.pass());
    }

    @Test
    void chat_hardRejectedQuery_rejectedWithoutPlanning() {        String longQuery = "查".repeat(2500);
        ChatRequest request = ChatRequest.builder()
                .query(longQuery)
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery(longQuery);
        processedQuery.setIntent(QueryIntent.TOOL);
        processedQuery.setCleanResult(CleanResult.builder()
                .cleanedText("查".repeat(2000))
                .truncated(true)
                .hardRejected(true)
                .boundary(TruncationBoundary.CODE_POINT)
                .originalCodePoints(2500)
                .build());
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("长度上限"));
        verifyNoInteractions(plannerService);
    }

    @Test
    void chat_truncatedQuery_addsTruncationIssue() {
        String longQuery = "查".repeat(1200);
        ChatRequest request = ChatRequest.builder()
                .query(longQuery)
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery(longQuery);
        processedQuery.setIntent(QueryIntent.TOOL);
        processedQuery.setCleanResult(CleanResult.builder()
                .cleanedText(longQuery)
                .truncated(true)
                .hardRejected(false)
                .boundary(TruncationBoundary.SENTENCE)
                .originalCodePoints(1200)
                .build());
        when(queryRouter.route(anyString(), any())).thenReturn(processedQuery);

        AgentResponse agentResponse = AgentResponse.success("答案", "task1", 1);
        when(plannerService.plan(any(), anyString(), anyString())).thenReturn(agentResponse);

        MemoryManager.SessionContext emptySessionCtx = new MemoryManager.SessionContext("", Collections.emptyList(), null);
        when(memoryManager.getSessionContext(anyString())).thenReturn(emptySessionCtx);

        CritiqueResult critique = CritiqueResult.pass();
        when(selfCritiqueService.critique(any(), any(), any())).thenReturn(critique);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().getIssues().size());
        assertTrue(result.getData().getIssues().get(0).contains("截断"));
    }

    @Test
    void health_returnsSuccess() {
        ApiResponse<String> result = controller.health();
        assertTrue(result.isSuccess());
        assertEquals("Agent is running", result.getData());
    }
}
