package com.luutry.agent.planner.model;

import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.common.enums.DataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规划器执行完成后的最终Agent响应。
 * 包含答案、使用的数据来源、执行轨迹和元数据。
 */
public class AgentResponse {

    /** 给用户的文本回答 */
    private final String answer;

    /** 生成答案时使用的数据来源 */
    private final List<DataSource> dataSources;

    /** 执行过程中的所有ReAct步骤（用于可观测性） */
    private final List<ReActStep> steps;

    /** 产生此响应的任务ID */
    private final String taskId;

    /** 任务是否成功完成 */
    private final boolean success;

    /** 执行的ReAct总轮次数 */
    private final int totalRounds;

    /** 结构化数据载体（工具结果、RAG片段等） */
    private final Map<String, Object> data;

    /** 执行产物（原始 ToolResult/RetrievalResult/MemoryEntry），供下游聚合与校验共用 */
    private final ExecutionResult executionResult;

    /** 从执行产物提取的事实集合，作为合成与校验的唯一真相源 */
    private final FactSet factSet;

    /** 模板渲染时实际绑定的事实键（entity#metric），供校验做零解析集合比对（复审 N5）；LLM 路径为空 */
    private final Set<String> usedFactKeys;

    private AgentResponse(Builder builder) {
        this.answer = builder.answer;
        this.dataSources = Collections.unmodifiableList(builder.dataSources);
        this.steps = Collections.unmodifiableList(builder.steps);
        this.taskId = builder.taskId;
        this.success = builder.success;
        this.totalRounds = builder.totalRounds;
        this.data = Collections.unmodifiableMap(builder.data);
        this.executionResult = builder.executionResult;
        this.factSet = builder.factSet != null ? builder.factSet : FactSet.empty();
        this.usedFactKeys = builder.usedFactKeys != null
                ? Collections.unmodifiableSet(builder.usedFactKeys)
                : Collections.emptySet();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建一个简单的成功响应，仅包含答案。
     */
    public static AgentResponse success(String answer, String taskId, int totalRounds) {
        return builder()
                .answer(answer)
                .taskId(taskId)
                .success(true)
                .totalRounds(totalRounds)
                .build();
    }

    /**
     * 创建一个失败响应。
     */
    public static AgentResponse failure(String answer, String taskId) {
        return builder()
                .answer(answer)
                .taskId(taskId)
                .success(false)
                .totalRounds(0)
                .build();
    }

    public String getAnswer() {
        return answer;
    }

    public List<DataSource> getDataSources() {
        return dataSources;
    }

    public List<ReActStep> getSteps() {
        return steps;
    }

    public String getTaskId() {
        return taskId;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public ExecutionResult getExecutionResult() {
        return executionResult;
    }

    public FactSet getFactSet() {
        return factSet;
    }

    public Set<String> getUsedFactKeys() {
        return usedFactKeys;
    }

    @Override
    public String toString() {
        return "AgentResponse{success=" + success
                + ", taskId='" + taskId + "'"
                + ", rounds=" + totalRounds
                + ", sources=" + dataSources
                + ", answer='" + (answer != null ? answer.substring(0, Math.min(50, answer.length())) : "null") + "'}";
    }

    public static class Builder {
        private String answer = "";
        private final List<DataSource> dataSources = new ArrayList<>();
        private final List<ReActStep> steps = new ArrayList<>();
        private String taskId = "";
        private boolean success = false;
        private int totalRounds = 0;
        private Map<String, Object> data = Collections.emptyMap();
        private ExecutionResult executionResult;
        private FactSet factSet;
        private Set<String> usedFactKeys = Collections.emptySet();

        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public Builder addDataSource(DataSource source) {
            if (source != null && !dataSources.contains(source)) {
                dataSources.add(source);
            }
            return this;
        }

        public Builder addStep(ReActStep step) {
            if (step != null) {
                steps.add(step);
            }
            return this;
        }

        public Builder steps(List<ReActStep> steps) {
            if (steps != null) {
                this.steps.clear();
                this.steps.addAll(steps);
            }
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder totalRounds(int totalRounds) {
            this.totalRounds = totalRounds;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data != null ? data : Collections.emptyMap();
            return this;
        }

        public Builder executionResult(ExecutionResult executionResult) {
            this.executionResult = executionResult;
            return this;
        }

        public Builder factSet(FactSet factSet) {
            this.factSet = factSet;
            return this;
        }

        public Builder usedFactKeys(Set<String> usedFactKeys) {
            this.usedFactKeys = usedFactKeys != null ? usedFactKeys : Collections.emptySet();
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(this);
        }
    }
}
