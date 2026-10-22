package com.luutry.agent.planner.model;

/**
 * ReAct（推理 + 行动）循环中的单个步骤。
 * 每个步骤记录规划器的思考过程、执行的动作
 * 以及动作执行后的观察结果。
 */
public class ReActStep {

    /** 该步骤的推理思考过程 */
    private final String thought;

    /** 决策类型（CALL_TOOL、CALL_RAG、READ_MEMORY、DIRECT_ANSWER、FINISH） */
    private final PlannerDecision actionType;

    /** 动作详情（如工具名称、RAG查询、记忆查询） */
    private final String actionDetail;

    /** 动作执行的观察结果/结果 */
    private String observation;

    /** 任务执行中的轮次索引（从0开始） */
    private final int roundIndex;

    /** 该步骤创建时的时间戳 */
    private final long timestampMs;

    public ReActStep(String thought, PlannerDecision actionType, String actionDetail, int roundIndex) {
        this.thought = thought;
        this.actionType = actionType;
        this.actionDetail = actionDetail;
        this.roundIndex = roundIndex;
        this.timestampMs = System.currentTimeMillis();
    }

    /**
     * 创建一个无动作详情的FINISH步骤。
     */
    public static ReActStep finish(String thought, int roundIndex) {
        return new ReActStep(thought, PlannerDecision.FINISH, "", roundIndex);
    }

    /**
     * 创建一个CALL_TOOL步骤。
     */
    public static ReActStep callTool(String thought, String toolName, int roundIndex) {
        return new ReActStep(thought, PlannerDecision.CALL_TOOL, toolName, roundIndex);
    }

    /**
     * 创建一个CALL_RAG步骤。
     */
    public static ReActStep callRag(String thought, String ragQuery, int roundIndex) {
        return new ReActStep(thought, PlannerDecision.CALL_RAG, ragQuery, roundIndex);
    }

    /**
     * 创建一个READ_MEMORY步骤。
     */
    public static ReActStep readMemory(String thought, String memoryQuery, int roundIndex) {
        return new ReActStep(thought, PlannerDecision.READ_MEMORY, memoryQuery, roundIndex);
    }

    /**
     * 创建一个DIRECT_ANSWER步骤。
     */
    public static ReActStep directAnswer(String thought, int roundIndex) {
        return new ReActStep(thought, PlannerDecision.DIRECT_ANSWER, "", roundIndex);
    }

    public String getThought() {
        return thought;
    }

    public PlannerDecision getActionType() {
        return actionType;
    }

    public String getActionDetail() {
        return actionDetail;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    @Override
    public String toString() {
        return "ReActStep{round=" + roundIndex
                + ", thought='" + thought + "'"
                + ", action=" + actionType
                + ", detail='" + actionDetail + "'"
                + ", hasObservation=" + (observation != null && !observation.isBlank()) + "}";
    }
}
