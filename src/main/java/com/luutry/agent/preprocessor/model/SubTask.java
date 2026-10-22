package com.luutry.agent.preprocessor.model;

import java.util.Objects;

/**
 * 复合查询分解的子任务模型。
 * 每个子任务代表一个独立的查询片段，
 * 带有自己的意图分类。
 */
public class SubTask {

    /** 子任务序号索引（从0开始） */
    private final int index;

    /** 子任务查询文本 */
    private final String query;

    /** 该子任务的分类意图 */
    private QueryIntent intent;

    /** 该子任务是否依赖前序子任务的结果 */
    private boolean dependent;

    /** 优先级顺序（数值越小优先级越高） */
    private int priority;

    public SubTask(int index, String query) {
        this.index = index;
        this.query = Objects.requireNonNull(query, "SubTask query must not be null");
        this.intent = QueryIntent.CHITCHAT;
        this.dependent = false;
        this.priority = index;
    }

    public int getIndex() {
        return index;
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

    public boolean isDependent() {
        return dependent;
    }

    public void setDependent(boolean dependent) {
        this.dependent = dependent;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "SubTask{index=" + index
                + ", query='" + query + "'"
                + ", intent=" + intent
                + ", dependent=" + dependent + "}";
    }
}
