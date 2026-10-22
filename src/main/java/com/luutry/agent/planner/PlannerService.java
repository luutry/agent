package com.luutry.agent.planner;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.FactExtractor;
import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.planner.model.*;
import com.luutry.agent.planner.react.ReActEngine;
import com.luutry.agent.planner.synthesis.DataTemplateRenderer;
import com.luutry.agent.planner.task.TaskDecomposer;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.llm.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一规划器服务入口。
 * 接收预处理后的查询，分解为子任务，
 * 通过ReAct引擎执行每个任务，并生成AgentResponse。
 */
@Slf4j
@Service
public class PlannerService {

    private final TaskDecomposer taskDecomposer;
    private final ReActEngine reActEngine;
    private final LlmService llmService;
    private final FactExtractor factExtractor;
    private final DataTemplateRenderer templateRenderer;

    public PlannerService(TaskDecomposer taskDecomposer, ReActEngine reActEngine, LlmService llmService,
                          FactExtractor factExtractor, DataTemplateRenderer templateRenderer) {
        this.taskDecomposer = Objects.requireNonNull(taskDecomposer);
        this.reActEngine = Objects.requireNonNull(reActEngine);
        this.llmService = Objects.requireNonNull(llmService);
        this.factExtractor = Objects.requireNonNull(factExtractor);
        this.templateRenderer = Objects.requireNonNull(templateRenderer);
    }

    /**
     * 规划并执行预处理后的查询，返回携带唯一真相源（ExecutionResult + FactSet）的 AgentResponse。
     *
     * @param processedQuery 带意图和实体的预处理查询
     * @param userId         当前用户ID
     * @param sessionId      当前会话ID
     * @return AgentResponse，包含答案、数据来源、执行产物与事实集合
     */
    public AgentResponse plan(ProcessedQuery processedQuery, String userId, String sessionId) {
        if (processedQuery == null || processedQuery.getFinalQuery().isBlank()) {
            return AgentResponse.failure("无法理解您的输入，请重新描述。", "no-task");
        }

        QueryIntent intent = processedQuery.getIntent();
        log.info("PlannerService starting: intent={}, query='{}'", intent, processedQuery.getFinalQuery());

        // 步骤1：分解为子任务
        List<AgentTask> tasks = taskDecomposer.decompose(processedQuery);
        if (tasks.isEmpty()) {
            return AgentResponse.failure("未能生成可执行任务，请重试。", "no-task");
        }

        // 步骤2：通过ReAct引擎执行，合并各任务产物为单一真相载体
        ExecutionResult.Builder execBuilder = ExecutionResult.builder().taskId(tasks.get(0).getId());
        List<ReActStep> allSteps = new ArrayList<>();
        int totalRounds = 0;
        boolean anySuccess = false;

        for (AgentTask task : tasks) {
            // 复合查询按子任务自己的意图执行；单意图查询沿用顶层意图
            QueryIntent taskIntent = task.getIntent() != null ? task.getIntent() : intent;
            ExecutionResult taskResult = reActEngine.execute(task, taskIntent, userId, sessionId);
            taskResult.getToolResults().forEach(execBuilder::addToolResult);
            taskResult.getRetrievalResults().forEach(execBuilder::addRetrievalResult);
            taskResult.getMemoryEntries().forEach(execBuilder::addMemoryEntry);
            taskResult.getSteps().forEach(execBuilder::addStep);
            taskResult.getDataSources().forEach(execBuilder::addDataSource);

            allSteps.addAll(task.getSteps());
            totalRounds += taskResult.getTotalRounds();
            if (taskResult.isSuccess()) {
                anySuccess = true;
            }
        }

        ExecutionResult executionResult = execBuilder.success(anySuccess).totalRounds(totalRounds).build();

        // 步骤3：事实化——从原始 ToolResult 提取唯一真相源，合成与校验共用
        FactSet factSet = factExtractor.extract(executionResult.getToolResults());

        // 步骤4：合成答案（纯数据走模板渲染，其余走 LLM）
        Composed composed = composeAnswer(processedQuery, executionResult, factSet, intent, anySuccess);

        AgentResponse.Builder responseBuilder = AgentResponse.builder()
                .answer(composed.answer())
                .taskId(tasks.get(0).getId())
                .success(anySuccess)
                .totalRounds(totalRounds)
                .steps(allSteps)
                .executionResult(executionResult)
                .factSet(factSet)
                .usedFactKeys(composed.usedFactKeys());
        executionResult.getDataSources().forEach(responseBuilder::addDataSource);

        AgentResponse response = responseBuilder.build();
        log.info("PlannerService completed: success={}, rounds={}, sources={}",
                response.isSuccess(), response.getTotalRounds(), response.getDataSources());
        return response;
    }

    /**
     * 合成产物：答案文本 + 模板渲染时绑定的事实键（LLM 路径为空集）。
     */
    private record Composed(String answer, Set<String> usedFactKeys) {}

    /**
     * 合成最终答案。
     * <p>
     * 纯数据类回答（TOOL 意图且能确定指标）直接用 FactSet 模板渲染，确定性、零幻觉（P1-3），
     * 并回传渲染所用事实键，供校验做零解析集合比对（N5）；需解释/归因或无法模板化时回退 LLM 合成。
     * </p>
     */
    private Composed composeAnswer(ProcessedQuery processedQuery, ExecutionResult executionResult,
                                 FactSet factSet, QueryIntent intent, boolean anySuccess) {
        // N4：工具按数据权限截断时回传的透明声明，追加到最终答案（不含数字，不干扰校验）
        String scopeNotice = extractScopeNotice(executionResult);
        // 复合查询含多个指标/来源，单指标模板无法覆盖，交给 LLM 汇总后由 FactSet 硬校验兜底
        boolean singleIntentToolQuery = intent == QueryIntent.TOOL && !processedQuery.isComposite();
        if (singleIntentToolQuery && factSet != null && !factSet.isEmpty()) {
            Optional<DataTemplateRenderer.RenderResult> rendered =
                    templateRenderer.render(processedQuery.getFinalQuery(), factSet);
            if (rendered.isPresent()) {
                log.debug("Answer rendered from FactSet template, length={}", rendered.get().text().length());
                return new Composed(appendNotice(rendered.get().text(), scopeNotice), rendered.get().usedFactKeys());
            }
        }

        // TODO(doc/TODO/012_绩效查询服务端确定性回答.md): 绩效查询当前只能安全拒答，待周期口径确认后服务端确定性渲染
        List<String> observations = executionResult.getSteps().stream()
                .map(ReActStep::getObservation)
                .filter(obs -> obs != null && !obs.isBlank())
                .collect(Collectors.toList());
        return new Composed(appendNotice(
                synthesizeAnswer(processedQuery, observations, intent, anySuccess), scopeNotice),
                Collections.emptySet());
    }

    /**
     * 从工具结果中提取数据权限截断声明（若有）。
     */
    private String extractScopeNotice(ExecutionResult executionResult) {
        for (ToolResult tr : executionResult.getToolResults()) {
            if (tr != null && tr.isSuccess() && tr.getData() != null) {
                Object notice = tr.getData().get(ToolResult.SCOPE_NOTICE_KEY);
                if (notice != null) {
                    return notice.toString();
                }
            }
        }
        return null;
    }

    private String appendNotice(String answer, String notice) {
        return notice != null ? answer + notice : answer;
    }

    /**
     * 使用LLM从收集到的观察结果中合成最终答案。
     * LLM调用失败时回退到原始拼接。
     *
     * @param processedQuery 原始预处理查询
     * @param observations   ReAct步骤的观察结果文本列表
     * @param intent         分类后的查询意图
     * @param anySuccess     是否至少有一个任务成功完成
     * @return 合成的答案文本
     */
    private String synthesizeAnswer(ProcessedQuery processedQuery, List<String> observations,
                                    QueryIntent intent, boolean anySuccess) {
        if (observations.isEmpty()) {
            return anySuccess ? "查询完成，但未获取到有效结果。" : "执行过程中遇到问题，请稍后重试。";
        }

        try {
            String intentCode = intent != null ? intent.getCode() : "unknown";
            String llmAnswer = llmService.generateAnswer(
                    processedQuery.getFinalQuery(), observations, intentCode);

            if (llmAnswer != null && !llmAnswer.isBlank()) {
                log.debug("LLM synthesized answer: length={}", llmAnswer.length());
                return llmAnswer;
            }
        } catch (Exception e) {
            log.warn("LLM answer synthesis failed, falling back to raw concatenation: {}", e.getMessage());
        }

        // 回退：拼接观察结果
        return String.join("\n", observations).trim();
    }
}
