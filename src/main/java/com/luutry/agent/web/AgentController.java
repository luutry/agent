package com.luutry.agent.web;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.common.exception.ErrorCodes;
import com.luutry.agent.common.model.dto.ChatRequest;
import com.luutry.agent.common.model.vo.ApiResponse;
import com.luutry.agent.common.model.vo.ChatResponse;
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
import com.luutry.agent.security.auth.UserDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private final UserDirectory userDirectory;

    public AgentController(QueryRouter queryRouter,
                           PlannerService plannerService,
                           ContextAggregator contextAggregator,
                           SelfCritiqueService selfCritiqueService,
                           MemoryManager memoryManager,
                           UserDirectory userDirectory) {
        this.queryRouter = Objects.requireNonNull(queryRouter);
        this.plannerService = Objects.requireNonNull(plannerService);
        this.contextAggregator = Objects.requireNonNull(contextAggregator);
        this.selfCritiqueService = Objects.requireNonNull(selfCritiqueService);
        this.memoryManager = Objects.requireNonNull(memoryManager);
        this.userDirectory = Objects.requireNonNull(userDirectory);
    }

    /**
     * 对话端点：将用户查询经过完整Agent流水线处理。
     *
     * @param request 对话请求，包含query、userId、sessionId和历史记录
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
        log.info("Chat request received: userId={}, sessionId={}, queryLength={}",
                request.getUserId(), request.getSessionId(),
                request.getQuery() != null ? request.getQuery().length() : 0);

        // 步骤2：请求入口装配 UserContext，供工具查询层做数据权限前置过滤（R2）
        UserContext userContext = userDirectory.resolve(request.getUserId());
        UserContextHolder.set(userContext);
        try {
            // 步骤3：预处理Query
            // 历史以服务端会话记忆为准（客户端 history 可伪造），无记忆时回退客户端历史并归一化顺序
            ProcessedQuery processedQuery = queryRouter.route(
                    request.getQuery(),
                    resolveHistory(request)
            );
            log.debug("Query preprocessed: intent={}, confidence={}", processedQuery.getIntent(), processedQuery.getConfidence());

            // 步骤3.1：超长拒答（R7 超限必须中止并说明原因，不能截断后照常回答）
            if (processedQuery.isHardRejected()) {
                log.warn("Query rejected by hard threshold: userId={}, codePoints={}",
                        request.getUserId(), processedQuery.getCleanResult() != null
                                ? processedQuery.getCleanResult().getOriginalCodePoints() : 0);
                return ApiResponse.fail(ErrorCodes.PARAM_ERROR.getCode(),
                        "查询超过长度上限，请精简问题或拆分为多个提问后重试");
            }

            // 步骤4：规划并执行任务，产出携带唯一真相源（ExecutionResult + FactSet）的响应
            AgentResponse agentResponse = plannerService.plan(
                    processedQuery, request.getUserId(), request.getSessionId());
            log.debug("Planner completed: success={}, rounds={}", agentResponse.isSuccess(), agentResponse.getTotalRounds());

            // 步骤5：从 ExecutionResult 单一真相源取原始数据，叠加会话历史后聚合上下文
            List<ConversationTurn> recentTurns = Collections.emptyList();
            try {
                MemoryManager.SessionContext sessionCtx = memoryManager.getSessionContext(request.getSessionId());
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
            rememberTurn(request, processedQuery, finalAnswer);

            log.info("Chat response sent: verdict={}, answer length={}, factCount={}",
                    critique.getVerdict(), finalAnswer != null ? finalAnswer.length() : 0,
                    agentResponse.getFactSet().size());
            return ApiResponse.success(chatResponse);

        } catch (Exception e) {
            log.error("Error processing chat request: {}", e.getMessage(), e);
            return ApiResponse.fail(ErrorCodes.SYSTEM_ERROR.getCode(), "Internal error processing request");
        } finally {
            // 防止线程复用导致用户上下文泄露
            UserContextHolder.clear();
        }
    }

    /**
     * 组装指代消解用的历史：会话记忆优先，无记忆时回退客户端历史。
     * <p>
     * 契约：客户端 history 按"最旧在前"传入，这里统一收敛成"最新在前"，
     * 且只保留最近 {@link AgentConstants#COREFERENCE_HISTORY_WINDOW} 轮，
     * 防止调用方伪造超长上下文。
     * </p>
     */
    List<String> resolveHistory(ChatRequest request) {
        int window = AgentConstants.COREFERENCE_HISTORY_WINDOW;
        try {
            List<ConversationTurn> memoryTurns = memoryManager.getRecentTurns(request.getSessionId(), window);
            if (memoryTurns != null && !memoryTurns.isEmpty()) {
                List<String> turns = new ArrayList<>();
                for (ConversationTurn turn : memoryTurns) {
                    if (turn != null && turn.getUserMessage() != null && !turn.getUserMessage().isBlank()) {
                        turns.add(turn.getUserMessage());
                    }
                }
                if (!turns.isEmpty()) {
                    // 存储按时间顺序返回（最旧在前），消解器约定最新在前
                    Collections.reverse(turns);
                    return turns;
                }
            }
        } catch (Throwable t) {
            log.warn("读取会话记忆失败，回退客户端历史: {}", t.getMessage());
        }
        return normalizeClientHistory(request.getHistory(), window);
    }

    /** 客户端历史归一化：保留最近 window 轮，并反转成"最新在前"。 */
    private List<String> normalizeClientHistory(List<String> history, int window) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> nonBlank = new ArrayList<>(history.size());
        for (String turn : history) {
            if (turn != null && !turn.isBlank()) {
                nonBlank.add(turn);
            }
        }
        int from = Math.max(0, nonBlank.size() - window);
        List<String> recent = new ArrayList<>(nonBlank.subList(from, nonBlank.size()));
        Collections.reverse(recent);
        return recent;
    }

    /** 写入一轮成功对话；存脱敏后的 query，避免历史成为新的 PII 出域通道。 */
    private void rememberTurn(ChatRequest request, ProcessedQuery processedQuery, String answer) {
        try {
            String userMessage = processedQuery.getCleanedQuery();
            if (userMessage == null || userMessage.isBlank() || answer == null) {
                return;
            }
            int turnNumber = memoryManager.getSessionTurnCount(request.getSessionId()) + 1;
            memoryManager.addToSession(request.getSessionId(), request.getUserId(),
                    ConversationTurn.builder()
                            .turnNumber(turnNumber)
                            .userMessage(userMessage)
                            .assistantMessage(answer)
                            .build());
        } catch (Throwable t) {
            log.warn("写入会话记忆失败: {}", t.getMessage());
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
