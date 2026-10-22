package com.luutry.agent.planner.decision;

import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.planner.model.PlannerDecision;
import com.luutry.agent.planner.model.ReActStep;
import com.luutry.agent.preprocessor.model.QueryIntent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于规则的决策器实现。
 * 使用意图分类和步骤历史来确定
 * ReAct循环中的下一步动作。
 */
@Component
public class RuleBasedDecisionMaker implements DecisionMaker {

    @Override
    public PlannerDecision decide(AgentTask task, QueryIntent queryIntent) {
        // 安全保护：达到最大轮次时强制结束
        if (task.isMaxRoundsReached()) {
            return PlannerDecision.FINISH;
        }

        int round = task.getCurrentRound();
        List<ReActStep> steps = task.getSteps();

        // 第一步：根据意图决策
        if (round == 0) {
            return decideFirstStep(queryIntent);
        }

        // 后续步骤：基于之前的观察结果评估
        return decideNextStep(task, queryIntent, steps);
    }

    /**
     * 根据查询意图决定第一步动作。
     */
    private PlannerDecision decideFirstStep(QueryIntent intent) {
        if (intent == null) {
            return PlannerDecision.DIRECT_ANSWER;
        }

        return switch (intent) {
            case TOOL -> PlannerDecision.CALL_TOOL;
            case RAG -> PlannerDecision.CALL_RAG;
            case MIXED -> PlannerDecision.CALL_TOOL; // 混合意图：先调用Tool，下一轮调用RAG
            case CHITCHAT -> PlannerDecision.DIRECT_ANSWER;
        };
    }

    /**
     * 根据任务状态和历史步骤决定下一步动作。
     */
    private PlannerDecision decideNextStep(AgentTask task, QueryIntent queryIntent, List<ReActStep> steps) {
        if (steps.isEmpty()) {
            return PlannerDecision.FINISH;
        }

        ReActStep lastStep = steps.get(steps.size() - 1);

        // 如果上一步是直接回答，则结束
        if (lastStep.getActionType() == PlannerDecision.DIRECT_ANSWER) {
            return PlannerDecision.FINISH;
        }

        // 如果上一步是结束，继续结束
        if (lastStep.getActionType() == PlannerDecision.FINISH) {
            return PlannerDecision.FINISH;
        }

        // 混合意图：如果第一步调用了Tool，下一步应调用RAG
        if (queryIntent == QueryIntent.MIXED && lastStep.getActionType() == PlannerDecision.CALL_TOOL) {
            return PlannerDecision.CALL_RAG;
        }

        // 检查观察结果是否表明失败（回退到替代数据源）
        String observation = lastStep.getObservation();
        boolean isFailure = observation == null || observation.isBlank()
                || observation.contains("failed") || observation.contains("error")
                || observation.contains("No relevant") || observation.contains("No user context")
                || observation.contains("empty") || observation.contains("not found");

        if (isFailure) {
            // 回退链：TOOL → RAG → MEMORY → FINISH
            if (lastStep.getActionType() == PlannerDecision.CALL_TOOL) {
                return PlannerDecision.CALL_RAG;
            }
            if (lastStep.getActionType() == PlannerDecision.CALL_RAG) {
                return PlannerDecision.READ_MEMORY;
            }
            return PlannerDecision.FINISH;
        }

        // 观察结果包含有效数据 → 结束
        return PlannerDecision.FINISH;
    }
}
