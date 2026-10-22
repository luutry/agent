package com.luutry.agent.planner.decision;

import com.luutry.agent.planner.model.AgentTask;
import com.luutry.agent.planner.model.PlannerDecision;
import com.luutry.agent.preprocessor.model.QueryIntent;

/**
 * 决策器接口。
 * 根据当前任务状态和执行历史，
 * 确定规划器应执行的下一步动作。
 */
public interface DecisionMaker {

    /**
     * 为给定任务决定下一步动作。
     *
     * @param task        带执行状态的当前Agent任务
     * @param queryIntent 原始查询的分类意图
     * @return 规划器对下一步的决策
     */
    PlannerDecision decide(AgentTask task, QueryIntent queryIntent);
}
