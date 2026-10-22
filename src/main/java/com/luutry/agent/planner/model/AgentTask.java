package com.luutry.agent.planner.model;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.preprocessor.model.QueryIntent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent任务模型，表示规划器中的工作单元。
 * 跟踪执行状态、ReAct步骤和结果。
 */
public class AgentTask {

    /** 任务唯一标识符 */
    private final String id;

    /** 任务查询文本 */
    private final String query;

    /** 该任务自己的意图（复合查询拆分后可能不同于顶层意图），为空时回退顶层意图 */
    private QueryIntent intent;

    /** 当前任务状态 */
    private TaskStatus status;

    /** 子任务列表（用于复合查询） */
    private final List<AgentTask> subTasks;

    /** ReAct执行步骤 */
    private final List<ReActStep> steps;

    /** 当前轮次索引（从0开始） */
    private int currentRound;

    /** 强制终止前允许的最大轮次数 */
    private final int maxRounds;

    /** 最终结果文本 */
    private String result;

    /** 父任务ID（根任务为null） */
    private String parentTaskId;

    public AgentTask(String query) {
        this(UUID.randomUUID().toString(), query, AgentConstants.PLANNER_MAX_ROUNDS);
    }

    public AgentTask(String id, String query, int maxRounds) {
        this.id = Objects.requireNonNull(id, "Task id must not be null");
        this.query = Objects.requireNonNull(query, "Task query must not be null");
        this.maxRounds = maxRounds;
        this.status = TaskStatus.PENDING;
        this.subTasks = new ArrayList<>();
        this.steps = new ArrayList<>();
        this.currentRound = 0;
    }

    public String getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public QueryIntent getIntent() {
        return intent;
    }

    public void setIntent(QueryIntent intent) {
        this.intent = intent;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public List<AgentTask> getSubTasks() {
        return Collections.unmodifiableList(subTasks);
    }

    public void addSubTask(AgentTask subTask) {
        if (subTask != null) {
            subTask.setParentTaskId(this.id);
            subTasks.add(subTask);
        }
    }

    public List<ReActStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public void addStep(ReActStep step) {
        if (step != null) {
            steps.add(step);
        }
    }

    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * 轮次计数器加1并返回新值。
     *
     * @return 加1后的轮次索引
     */
    public int incrementRound() {
        return ++currentRound;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    /**
     * 检查任务是否已超过最大允许轮次。
     */
    public boolean isMaxRoundsReached() {
        return currentRound >= maxRounds;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * 检查是否为根任务（无父任务）。
     */
    public boolean isRoot() {
        return parentTaskId == null;
    }

    /**
     * 检查该任务是否有子任务。
     */
    public boolean hasSubTasks() {
        return !subTasks.isEmpty();
    }

    /**
     * 获取最后一个ReAct步骤，无步骤时返回null。
     */
    public ReActStep getLastStep() {
        if (steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1);
    }

    @Override
    public String toString() {
        return "AgentTask{id='" + id + "', query='" + query + "', status=" + status
                + ", round=" + currentRound + "/" + maxRounds
                + ", subTasks=" + subTasks.size() + "}";
    }
}
