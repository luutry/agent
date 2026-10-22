package com.luutry.agent.planner.model;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.tool.model.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ReAct 执行的唯一真相载体，携带原始结构化产物。
 * <p>
 * 用途：替代 PipelineEnvelope 旁路，让下游（合成、聚合、校验）都从同一份原始对象读取，
 * 消除"两份真相"（P0-3）。合成与校验共享由此派生的 FactSet。
 * </p>
 */
public class ExecutionResult {

    private final List<ToolResult> toolResults;
    private final List<RetrievalResult> retrievalResults;
    private final List<MemoryEntry> memoryEntries;
    private final List<ReActStep> steps;
    private final Set<DataSource> dataSources;
    private final boolean success;
    private final int totalRounds;
    private final String taskId;

    private ExecutionResult(Builder builder) {
        this.toolResults = Collections.unmodifiableList(builder.toolResults);
        this.retrievalResults = Collections.unmodifiableList(builder.retrievalResults);
        this.memoryEntries = Collections.unmodifiableList(builder.memoryEntries);
        this.steps = Collections.unmodifiableList(builder.steps);
        this.dataSources = Collections.unmodifiableSet(builder.dataSources);
        this.success = builder.success;
        this.totalRounds = builder.totalRounds;
        this.taskId = builder.taskId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 空执行结果，用于失败或无产物场景。 */
    public static ExecutionResult empty(String taskId) {
        return builder().taskId(taskId).success(false).build();
    }

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public List<RetrievalResult> getRetrievalResults() {
        return retrievalResults;
    }

    public List<MemoryEntry> getMemoryEntries() {
        return memoryEntries;
    }

    public List<ReActStep> getSteps() {
        return steps;
    }

    public Set<DataSource> getDataSources() {
        return dataSources;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public String getTaskId() {
        return taskId;
    }

    public static class Builder {
        private final List<ToolResult> toolResults = new ArrayList<>();
        private final List<RetrievalResult> retrievalResults = new ArrayList<>();
        private final List<MemoryEntry> memoryEntries = new ArrayList<>();
        private final List<ReActStep> steps = new ArrayList<>();
        private final Set<DataSource> dataSources = new LinkedHashSet<>();
        private boolean success = false;
        private int totalRounds = 0;
        private String taskId = "";

        public Builder addToolResult(ToolResult result) {
            if (result != null) {
                toolResults.add(result);
            }
            return this;
        }

        public Builder addRetrievalResult(RetrievalResult result) {
            if (result != null) {
                retrievalResults.add(result);
            }
            return this;
        }

        public Builder addMemoryEntry(MemoryEntry entry) {
            if (entry != null) {
                memoryEntries.add(entry);
            }
            return this;
        }

        public Builder addStep(ReActStep step) {
            if (step != null) {
                steps.add(step);
            }
            return this;
        }

        public Builder addDataSource(DataSource source) {
            if (source != null) {
                dataSources.add(source);
            }
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

        public Builder taskId(String taskId) {
            this.taskId = taskId != null ? taskId : "";
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }
}
