package com.luutry.agent.preprocessor.model;

import java.util.Objects;

/**
 * 管道中的独立查询单元。
 * 复合判定后每个 unit 独立走 intent/slots/retrieval 三件事。
 */
public class QueryUnit {

    /** 单元序号（从 0 开始） */
    private final int index;

    /** 单元查询原文（消解后、未扩展） */
    private final String query;

    /** 该单元的分类意图 */
    private QueryIntent intent;

    /** 该单元的分类置信度 [0.0, 1.0] */
    private double confidence;

    public QueryUnit(int index, String query) {
        this.index = index;
        this.query = Objects.requireNonNull(query, "QueryUnit query must not be null");
        this.intent = QueryIntent.CHITCHAT;
        this.confidence = 0.0;
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

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    @Override
    public String toString() {
        return "QueryUnit{index=" + index
                + ", intent=" + intent
                + ", confidence=" + String.format("%.2f", confidence)
                + ", query='" + query + "'}";
    }
}
