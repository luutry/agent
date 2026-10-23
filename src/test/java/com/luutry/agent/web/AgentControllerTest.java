package com.luutry.agent.web;

import com.luutry.agent.common.model.dto.ChatRequest;
import com.luutry.agent.common.exception.ErrorCodes;
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
import com.luutry.agent.security.auth.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private ContextAggregator contextAggregator;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        contextAggregator = new DefaultContextAggregator();
        controller = new AgentController(queryRouter, plannerService, contextAggregator,
                selfCritiqueService, memoryManager);
        // 身份由 SSO 过滤器装配，单测直接模拟已认证上下文
        UserContextHolder.set(UserContext.builder().userId("user1").managedOrgIds(List.of("DEPT_TECH")).build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
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
    void chat_delegatesQueryAndSessionIdToRouter() {
        ChatRequest request = ChatRequest.builder()
                .query("他们的绩效")
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("他们的绩效");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);

        controller.chat(request);

        // 历史不再经 Controller 中转：只交 query 与 sessionId，历史由消解器按需拉取
        verify(queryRouter).route(eq("他们的绩效"), eq("session1"));
        verify(memoryManager, never()).getRecentTurns(anyString(), anyInt());
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

        controller.chat(request);

        ArgumentCaptor<ConversationTurn> captor = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(memoryManager).addToSession(eq("session1"), eq("user1"), captor.capture());
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
    void chat_noAuthenticatedContext_returnsUnauthorized() {
        UserContextHolder.clear();
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("session1")
                .build();

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.AUTH_FAILED.getCode(), result.getCode());
        verifyNoInteractions(plannerService);
    }

    @Test
    void chat_bodyUserIdMismatchToken_returnsPermissionDenied() {
        // 令牌身份为 user1（setUp 已装配），请求体声明 user2 → 不一致，拒绝
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user2")
                .sessionId("session1")
                .build();

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.PERMISSION_DENIED.getCode(), result.getCode());
        verifyNoInteractions(plannerService);
    }

    @Test
    void chat_blankSessionId_generatesAndReturnsTicket() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("查看技术部人员");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess());
        String sessionId = result.getData().getSessionId();
        assertNotNull(sessionId);
        assertTrue(sessionId.startsWith("sess_"), "服务端签发的会话票据应带 sess_ 前缀");
        verify(plannerService).plan(any(), eq("user1"), eq(sessionId));
        verify(memoryManager).addToSession(eq(sessionId), eq("user1"), any());
    }

    @Test
    void chat_invalidSessionIdFormat_rejected() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("bad:id")
                .build();

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.PARAM_ERROR.getCode(), result.getCode());
        verifyNoInteractions(plannerService);
    }

    @Test
    void chat_sessionOwnedByAnotherUser_rejected() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("session1")
                .build();
        when(memoryManager.getSessionUserId("session1")).thenReturn(Optional.of("user2"));

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.PERMISSION_DENIED.getCode(), result.getCode());
        verifyNoInteractions(queryRouter);
        verifyNoInteractions(plannerService);
    }

    @Test
    void chat_ownershipCheckFails_degradesInsteadOfBreaking() {
        ChatRequest request = ChatRequest.builder()
                .query("查看技术部人员")
                .userId("user1")
                .sessionId("session1")
                .build();

        ProcessedQuery processedQuery = new ProcessedQuery("查看技术部人员");
        processedQuery.setIntent(QueryIntent.TOOL);
        stubSuccessfulPipeline(processedQuery);
        when(memoryManager.getSessionUserId("session1"))
                .thenThrow(new RuntimeException("memory down"));

        ApiResponse<ChatResponse> result = controller.chat(request);

        assertTrue(result.isSuccess(), "会话存储故障不应阻断对话");
    }

    @Test
    void resetSession_clearsSession() {
        when(memoryManager.getSessionUserId("session1")).thenReturn(Optional.of("user1"));

        ApiResponse<Void> result = controller.resetSession("session1");

        assertTrue(result.isSuccess());
        verify(memoryManager).clearSession("session1");
    }

    @Test
    void resetSession_otherUser_rejected() {
        when(memoryManager.getSessionUserId("session1")).thenReturn(Optional.of("user2"));

        ApiResponse<Void> result = controller.resetSession("session1");

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.PERMISSION_DENIED.getCode(), result.getCode());
        verify(memoryManager, never()).clearSession(any());
    }

    @Test
    void resetSession_invalidId_rejected() {
        ApiResponse<Void> result = controller.resetSession("bad:id");

        assertFalse(result.isSuccess());
        assertEquals(ErrorCodes.PARAM_ERROR.getCode(), result.getCode());
        verify(memoryManager, never()).clearSession(any());
    }

    @Test
    void health_returnsSuccess() {
        ApiResponse<String> result = controller.health();
        assertTrue(result.isSuccess());
        assertEquals("Agent is running", result.getData());
    }
}
