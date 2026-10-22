package com.luutry.agent.planner.react;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.planner.decision.DecisionMaker;
import com.luutry.agent.planner.model.*;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.rag.RagService;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.security.filter.ToolResultSanitizer;
import com.luutry.agent.tool.dispatcher.ToolDispatcher;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.tool.param.ToolParameterExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ReAct（推理 + 行动）引擎。
 * 为单个AgentTask执行“思考→决策→行动→观察”循环。
 */
@Slf4j
@Component
public class ReActEngine {

    private final DecisionMaker decisionMaker;
    private final ToolDispatcher toolDispatcher;
    private final RagService ragService;
    private final MemoryManager memoryManager;
    private final ToolParameterExtractor parameterExtractor;
    private final ToolResultSanitizer resultSanitizer;

    public ReActEngine(DecisionMaker decisionMaker,
                       ToolDispatcher toolDispatcher,
                       RagService ragService,
                       MemoryManager memoryManager,
                       ToolParameterExtractor parameterExtractor,
                       ToolResultSanitizer resultSanitizer) {
        this.decisionMaker = Objects.requireNonNull(decisionMaker);
        this.toolDispatcher = Objects.requireNonNull(toolDispatcher);
        this.ragService = Objects.requireNonNull(ragService);
        this.memoryManager = Objects.requireNonNull(memoryManager);
        this.parameterExtractor = Objects.requireNonNull(parameterExtractor);
        this.resultSanitizer = Objects.requireNonNull(resultSanitizer);
    }

    /**
     * 执行单个任务的ReAct循环，返回携带原始产物的 ExecutionResult（唯一真相源）。
     *
     * @param task      要执行的Agent任务
     * @param intent    分类后的查询意图
     * @param userId    当前用户ID（用于记忆访问）
     * @param sessionId 当前会话ID（用于记忆访问）
     * @return ExecutionResult，含原始 ToolResult/RetrievalResult/MemoryEntry 与步骤
     */
    public ExecutionResult execute(AgentTask task, QueryIntent intent, String userId, String sessionId) {
        task.setStatus(TaskStatus.RUNNING);
        ExecutionResult.Builder resultBuilder = ExecutionResult.builder().taskId(task.getId());
        log.info("ReAct engine started for task '{}', intent={}", task.getId(), intent);

        try {
            // 槽位只依赖查询本身，循环外抽一次，避免每轮重复解析
            // TODO(doc/TODO/014_工号脱敏与取参双通道.md): 现在取参读的是脱敏后的 task.query，
            // 工号一旦纳入脱敏就会取不到；需改成原文取参、脱敏文本进 Prompt 的双通道
            Map<String, Object> slots = parameterExtractor.extract(task.getQuery());

            while (!task.isMaxRoundsReached()) {
                int round = task.getCurrentRound();

                // 步骤1：思考 - 决定要执行的动作
                PlannerDecision decision = decisionMaker.decide(task, intent);
                log.debug("Round {}: decision={}", round, decision);

                // 步骤2：创建ReAct步骤
                ReActStep step = createStep(task, decision, round, slots);

                // 步骤3：行动 - 执行动作并记录观察结果，原始产物入真相载体
                String observation = executeAction(step, slots, userId, sessionId, resultBuilder);
                step.setObservation(observation);

                // 步骤4：记录步骤
                task.addStep(step);
                resultBuilder.addStep(step);
                resultBuilder.addDataSource(mapDecisionToSource(decision));
                task.incrementRound();

                // 步骤5：检查终止条件
                if (decision == PlannerDecision.FINISH
                        || decision == PlannerDecision.DIRECT_ANSWER) {
                    break;
                }
            }

            // 如果达到最大轮次但未显式结束，添加强制结束步骤
            if (task.isMaxRoundsReached()) {
                ReActStep finishStep = ReActStep.finish(
                        "Forced termination: max rounds (" + task.getMaxRounds() + ") reached",
                        task.getCurrentRound()
                );
                finishStep.setObservation("Max rounds reached, terminating");
                task.addStep(finishStep);
                resultBuilder.addStep(finishStep);
                log.warn("Task '{}' forced to finish after {} rounds", task.getId(), task.getMaxRounds());
            }

            // 从步骤中构建结果
            String result = buildResult(task);
            task.setResult(result);
            task.setStatus(TaskStatus.COMPLETED);

            log.info("ReAct engine completed for task '{}', rounds={}, status={}",
                    task.getId(), task.getCurrentRound(), task.getStatus());

            return resultBuilder
                    .success(true)
                    .totalRounds(task.getCurrentRound())
                    .build();

        } catch (Exception e) {
            log.error("ReAct engine error for task '{}': {}", task.getId(), e.getMessage(), e);
            task.setResult("Error during execution: " + e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            return resultBuilder
                    .success(false)
                    .totalRounds(task.getCurrentRound())
                    .build();
        }
    }

    /**
     * 将决策类型映射为数据来源，DIRECT_ANSWER/FINISH 无来源返回 null。
     */
    private com.luutry.agent.common.enums.DataSource mapDecisionToSource(PlannerDecision decision) {
        return switch (decision) {
            case CALL_TOOL -> com.luutry.agent.common.enums.DataSource.TOOL;
            case CALL_RAG -> com.luutry.agent.common.enums.DataSource.RAG;
            case READ_MEMORY -> com.luutry.agent.common.enums.DataSource.MEMORY;
            default -> null;
        };
    }

    /**
     * 根据决策创建ReAct步骤。工具选择结合已抽取槽位：有明确对象时优先精确工具。
     */
    private ReActStep createStep(AgentTask task, PlannerDecision decision, int round,
                                 Map<String, Object> slots) {
        String query = task.getQuery();

        return switch (decision) {
            case CALL_TOOL -> ReActStep.callTool(
                    "User query requires tool invocation for: " + query,
                    inferToolName(query, slots),
                    round
            );
            case CALL_RAG -> ReActStep.callRag(
                    "User query requires document retrieval for: " + query,
                    query,
                    round
            );
            case READ_MEMORY -> ReActStep.readMemory(
                    "Need to consult memory for: " + query,
                    query,
                    round
            );
            case DIRECT_ANSWER -> ReActStep.directAnswer(
                    "Query can be answered directly or is chitchat",
                    round
            );
            case FINISH -> ReActStep.finish(
                    "Task complete, sufficient information gathered",
                    round
            );
        };
    }

    /**
     * 执行步骤中指定的动作并返回观察结果。
     * 原始产物收集到 ExecutionResult.Builder，供下游共用同一份真相。
     */
    private String executeAction(ReActStep step, Map<String, Object> slots,
                                 String userId, String sessionId,
                                 ExecutionResult.Builder resultBuilder) {
        return switch (step.getActionType()) {
            case CALL_TOOL -> executeToolCall(step.getActionDetail(), slots, resultBuilder);
            case CALL_RAG -> executeRagCall(step.getActionDetail(), resultBuilder);
            case READ_MEMORY -> executeMemoryCall(step.getActionDetail(), userId, resultBuilder);
            case DIRECT_ANSWER -> "Direct answer mode";
            case FINISH -> "";
        };
    }

    /**
     * 执行工具调用并返回格式化的观察结果。
     * 将原始ToolResult收集到真相载体中，供下游上下文聚合与事实化使用。
     */
    private String executeToolCall(String toolName, Map<String, Object> slots,
                                   ExecutionResult.Builder resultBuilder) {
        ToolRequest request = ToolRequest.builder()
                .toolName(toolName)
                .parameters(slots != null ? slots : Map.of())
                .build();

        ToolResult result = toolDispatcher.dispatch(request);

        // 将原始产物收集到真相载体
        resultBuilder.addToolResult(result);

        if (result.isSuccess()) {
            return formatToolResult(result);
        } else {
            return "Tool call failed: " + result.getErrorMessage();
        }
    }

    /**
     * 执行RAG检索并返回格式化的观察结果。
     * 将原始RetrievalResult对象收集到真相载体中，供下游上下文聚合使用。
     */
    private String executeRagCall(String query, ExecutionResult.Builder resultBuilder) {
        List<RetrievalResult> results = ragService.retrieve(query);

        // 将原始产物收集到真相载体
        results.forEach(resultBuilder::addRetrievalResult);

        if (results.isEmpty()) {
            return "No relevant documents found";
        }

        return results.stream()
                .map(r -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(r.getChunk().getContent());
                    if (r.getProvenance() != null) {
                        sb.append(" ").append(r.getProvenance());
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * 执行记忆检索并返回格式化的观察结果。
     * 将原始MemoryEntry对象收集到真相载体中，供下游上下文聚合使用。
     */
    private String executeMemoryCall(String query, String userId, ExecutionResult.Builder resultBuilder) {
        if (userId == null || userId.isBlank()) {
            return "No user context for memory retrieval";
        }

        List<MemoryEntry> entries = memoryManager.retrieveRelevant(
                userId, query, AgentConstants.DEFAULT_MEMORY_SEARCH_TOP_K);

        // 将原始产物收集到真相载体
        entries.forEach(resultBuilder::addMemoryEntry);

        if (entries.isEmpty()) {
            return "No relevant memories found";
        }

        return entries.stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * 根据查询文本与已抽取槽位推断工具。
     * <p>
     * 有明确员工对象时优先精确工具（档案/绩效），否则按业务词回退；
     * 无员工对象时不选 employee_profile/performance_record，避免必然缺参失败。
     * </p>
     */
    private String inferToolName(String query, Map<String, Object> slots) {
        if (query == null) {
            return "employee_search";
        }
        boolean hasEmployee = slots != null && slots.containsKey("employeeId");
        boolean mentionsPerformance = query.contains("绩效") || query.contains("考核");
        boolean mentionsProfile = query.contains("档案") || query.contains("履历") || query.contains("简历");

        if (hasEmployee && mentionsPerformance) {
            return "performance_record";
        }
        if (hasEmployee && mentionsProfile) {
            return "employee_profile";
        }
        if (query.contains("团队") || query.contains("部门") || query.contains("盘点")) {
            return "team_metrics";
        }
        if (mentionsPerformance) {
            return "performance_record";
        }
        if (query.contains("组织") || query.contains("架构")) {
            return "org_tree";
        }
        return "employee_search";
    }

    /**
     * 将工具结果格式化为可读的观察文本。
     * 进 Prompt 前统一脱敏：剔除员工级绩效明细、掩码 PII（R3/R3.1）。
     */
    private String formatToolResult(ToolResult result) {
        Map<String, Object> data = resultSanitizer.sanitizeForLlm(result.getData());
        if (data.isEmpty()) {
            return "Tool returned empty result";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 从所有ReAct步骤中构建最终结果文本。
     */
    private String buildResult(AgentTask task) {
        StringBuilder sb = new StringBuilder();

        for (ReActStep step : task.getSteps()) {
            if (step.getObservation() != null && !step.getObservation().isBlank()) {
                sb.append(step.getObservation()).append("\n");
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            // 回退：对于闲聊类型，使用查询本身作为答案
            if (task.getSteps().stream().anyMatch(s -> s.getActionType() == PlannerDecision.DIRECT_ANSWER)) {
                return "您好，我是HR管理助手，请问有什么可以帮您？";
            }
            return "抱歉，暂时无法获取相关信息。";
        }

        return result;
    }
}
