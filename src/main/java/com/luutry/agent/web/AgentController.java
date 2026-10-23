package com.luutry.agent.web;

import com.luutry.agent.common.exception.ErrorCodes;
import com.luutry.agent.common.model.dto.ChatRequest;
import com.luutry.agent.common.model.vo.ApiResponse;
import com.luutry.agent.common.model.vo.ChatResponse;
import com.luutry.agent.common.util.SessionIds;
import com.luutry.agent.aggregate.ContextAggregator;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.critique.SelfCritiqueService;
import com.luutry.agent.critique.model.CritiqueResult;
import com.luutry.agent.critique.model.CritiqueVerdict;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.planner.PlannerService;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.planner.model.ExecutionResult;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.router.QueryRouter;
import com.luutry.agent.security.auth.UserContext;
import com.luutry.agent.security.auth.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent REST控制器，提供统一的对话API端点。
 * <p>
 * 编排完整流水线：UserContext 装配 → QueryRouter → PlannerService → ContextAggregator → SelfCritiqueService。
 * 请求入口解析并绑定 UserContext（R2 数据权限前置），聚合与校验统一从 ExecutionResult 单一真相源取数。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final QueryRouter queryRouter;
    private final PlannerService plannerService;
    private final ContextAggregator contextAggregator;
    private final SelfCritiqueService selfCritiqueService;
    private final MemoryManager memoryManager;

    public AgentController(QueryRouter queryRouter,
                           PlannerService plannerService,
                           ContextAggregator contextAggregator,
                           SelfCritiqueService selfCritiqueService,
                           MemoryManager memoryManager) {
        this.queryRouter = Objects.requireNonNull(queryRouter);
        this.plannerService = Objects.requireNonNull(plannerService);
        this.contextAggregator = Objects.requireNonNull(contextAggregator);
        this.selfCritiqueService = Objects.requireNonNull(selfCritiqueService);
        this.memoryManager = Objects.requireNonNull(memoryManager);
    }

    /**
     * 对话端点：将用户查询经过完整Agent流水线处理。
     *
     * @param request 对话请求，仅含 query、userId、sessionId；多轮历史由服务端会话记忆提供，客户端不传
     * @return 包含回答、数据源和校验信息的ApiResponse<ChatResponse>
     */
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        // 步骤1：校验请求
        if (request == null || !request.isValid()) {
            log.warn("Invalid chat request: missing required fields");
            return ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(), "Missing required fields: query, userId, sessionId");
        }

        // 只记长度，不记内容：清洗发生在下一步，此前打印原文等于绕开脱敏（R4）
        // 步骤2：身份以 SSO 过滤器装配的 UserContext 为准（令牌推导，不可被请求体伪造）；
        // 请求体 userId 仅作一致性校验，防止令牌身份与声明身份不符（R2/R3）
        UserContext userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null || userContext.getUserId().isBlank()) {
            log.warn("Rejected chat request: no authenticated identity in context");
            return ApiResponse.fail(ErrorCodes.AUTH_FAILED.getCode(), "未认证：缺少有效的登录令牌");
        }
        String authenticatedUserId = userContext.getUserId();
        String bodyUserId = request.getUserId();
        if (bodyUserId != null && !bodyUserId.isBlank() && !bodyUserId.equals(authenticatedUserId)) {
            log.warn("Rejected chat request: body userId mismatches token identity");
            return ApiResponse.fail(ErrorCodes.PERMISSION_DENIED.getCode(), "身份不一致：请求体 userId 与登录令牌不匹配");
        }

        // 步骤3：会话票据由服务端签发（缺省生成），并校验归属，防止拿别人的 sessionId 读历史
        String sessionId = resolveSessionId(request.getSessionId());
        if (sessionId == null) {
            log.warn("Rejected chat request: invalid sessionId format");
            return ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(), "sessionId 格式非法");
        }
        if (!ownsSession(sessionId, authenticatedUserId)) {
            log.warn("Rejected chat request: session belongs to another user");
            return ApiResponse.fail(ErrorCodes.PERMISSION_DENIED.getCode(), "会话归属校验失败");
        }

        log.info("Chat request received: userId={}, sessionId={}, queryLength={}",
                authenticatedUserId, sessionId,
                request.getQuery() != null ? request.getQuery().length() : 0);

        try {
            // 步骤4：预处理Query，历史由消解器按需从服务端会话记忆拉取（不再经 Controller 中转）
            ProcessedQuery processedQuery = queryRouter.route(request.getQuery(), sessionId);
            log.debug("Query preprocessed: intent={}, confidence={}", processedQuery.getIntent(), processedQuery.getConfidence());

            // 步骤3.1：超长拒答（R7 超限必须中止并说明原因，不能截断后照常回答）
            if (processedQuery.isHardRejected()) {
                log.warn("Query rejected by hard threshold: userId={}, codePoints={}",
                        authenticatedUserId, processedQuery.getCleanResult() != null
                                ? processedQuery.getCleanResult().getOriginalCodePoints() : 0);
                return ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(),
                        "查询超过长度上限，请精简问题或拆分为多个提问后重试");
            }

            // 步骤4：规划并执行任务，产出携带唯一真相源（ExecutionResult + FactSet）的响应
            AgentResponse agentResponse = plannerService.plan(
                    processedQuery, authenticatedUserId, sessionId);
            log.debug("Planner completed: success={}, rounds={}", agentResponse.isSuccess(), agentResponse.getTotalRounds());

            // 步骤5：从 ExecutionResult 单一真相源取原始数据，叠加会话历史后聚合上下文
            List<ConversationTurn> recentTurns = Collections.emptyList();
            try {
                MemoryManager.SessionContext sessionCtx = memoryManager.getSessionContext(sessionId);
                recentTurns = sessionCtx.getRecentTurns();
            } catch (Exception e) {
                log.debug("Failed to get session context: {}", e.getMessage());
            }

            ExecutionResult executionResult = agentResponse.getExecutionResult();
            AggregatedContext aggregatedContext = contextAggregator.aggregate(
                    executionResult != null ? executionResult.getToolResults() : Collections.emptyList(),
                    executionResult != null ? executionResult.getRetrievalResults() : Collections.emptyList(),
                    executionResult != null ? executionResult.getMemoryEntries() : Collections.emptyList(),
                    recentTurns
            );
            log.debug("Context aggregated: segments={}, totalTokens={}",
                    aggregatedContext.getSegmentCount(), aggregatedContext.getTotalTokens());

            // 步骤6：反思校验，FactSet 随 AgentResponse 一并传入做精确事实校验
            CritiqueResult critique = selfCritiqueService.critique(agentResponse, aggregatedContext, processedQuery);
            log.debug("Critique completed: verdict={}, confidence={}", critique.getVerdict(), critique.getConfidence());

            // 步骤7：构建最终响应
            String finalAnswer = agentResponse.getAnswer();
            if (critique.getRevisedAnswer() != null && critique.getVerdict() == CritiqueVerdict.REJECT) {
                finalAnswer = critique.getRevisedAnswer();
            }

            // 截断必须在响应里声明：用户有权知道答案只基于被截断后的查询（R7）
            List<String> issues = new ArrayList<>();
            if (critique.getIssues() != null) {
                issues.addAll(critique.getIssues());
            }
            if (processedQuery.isTruncated()) {
                issues.add("查询过长已被截断，本次回答仅基于截断后的内容；如需完整处理请拆分为多个问题");
            }

            ChatResponse chatResponse = ChatResponse.builder()
                    .sessionId(sessionId)
                    .answer(finalAnswer)
                    .sources(agentResponse.getDataSources())
                    .verdict(critique.getVerdict())
                    .confidence(critique.getConfidence())
                    .issues(issues)
                    .taskId(agentResponse.getTaskId())
                    .totalRounds(agentResponse.getTotalRounds())
                    .data(agentResponse.getData())
                    .build();

            // 步骤8：写入会话记忆，供下一轮指代消解与会话上下文使用（失败不影响本次响应）
            rememberTurn(authenticatedUserId, sessionId, processedQuery, finalAnswer);

            log.info("Chat response sent: verdict={}, answer length={}, factCount={}",
                    critique.getVerdict(), finalAnswer != null ? finalAnswer.length() : 0,
                    agentResponse.getFactSet().size());
            return ApiResponse.success(chatResponse);

        } catch (Exception e) {
            log.error("Error processing chat request: {}", e.getMessage(), e);
            return ApiResponse.fail(ErrorCodes.SYSTEM_ERROR.getCode(), "Internal error processing request");
        }
    }

    /**
     * 解析会话票据：客户端传了就用（校验格式），没传就由服务端签发。
     *
     * @return 合法会话标识；格式非法时返回 null
     */
    String resolveSessionId(String requested) {
        if (requested == null || requested.isBlank()) {
            return SessionIds.newId();
        }
        return SessionIds.isValid(requested) ? requested : null;
    }

    /**
     * 会话归属校验：会话不存在时视为新会话；存储不可用时放行（此时也读不到历史）。
     */
    boolean ownsSession(String sessionId, String userId) {
        try {
            Optional<String> owner = memoryManager.getSessionUserId(sessionId);
            return owner.isEmpty() || owner.get().equals(userId);
        } catch (Throwable t) {
            log.warn("会话归属校验降级（会话存储不可用）: {}", t.getMessage());
            return true;
        }
    }

    /** 写入一轮成功对话；存脱敏后的 query，避免历史成为新的 PII 出域通道。 */
    private void rememberTurn(String userId, String sessionId, ProcessedQuery processedQuery, String answer) {
        try {
            String userMessage = processedQuery.getCleanedQuery();
            if (userMessage == null || userMessage.isBlank() || answer == null) {
                return;
            }
            memoryManager.addToSession(sessionId, userId,
                    ConversationTurn.builder()
                            .userMessage(userMessage)
                            .assistantMessage(answer)
                            .build());
        } catch (Throwable t) {
            log.warn("写入会话记忆失败: {}", t.getMessage());
        }
    }

    /**
     * 清空会话：前端"新对话"或主动重置时调用，下次提问从零开始。
     *
     * @param sessionId 会话标识
     * @return 清空结果
     */
    @PostMapping("/session/{sessionId}/reset")
    public ApiResponse<Void> resetSession(@PathVariable("sessionId") String sessionId) {
        UserContext userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null || userContext.getUserId().isBlank()) {
            return ApiResponse.fail(ErrorCodes.AUTH_FAILED.getCode(), "未认证：缺少有效的登录令牌");
        }
        if (!SessionIds.isValid(sessionId)) {
            return ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(), "sessionId 格式非法");
        }
        if (!ownsSession(sessionId, userContext.getUserId())) {
            return ApiResponse.fail(ErrorCodes.PERMISSION_DENIED.getCode(), "会话归属校验失败");
        }
        try {
            memoryManager.clearSession(sessionId);
            log.info("Session reset: sessionId={}", sessionId);
            return ApiResponse.success();
        } catch (Throwable t) {
            log.warn("清空会话失败: {}", t.getMessage());
            return ApiResponse.fail(ErrorCodes.SYSTEM_ERROR.getCode(), "清空会话失败，请重试");
        }
    }

    /**
     * 健康检查端点。
     *
     * @return 简单成功响应，表示Agent正在运行
     */
    @PostMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Agent is running");
    }
}
