package com.luutry.agent.preprocessor.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 意图分类结果，包含所有意图类型的置信度分数。
 * 由IntentClassifier返回多分类评分。
 */
public class IntentResult {

    /** 分类后的意图 */
    private final QueryIntent intent;

    /** 分类意图的置信度分数 [0.0, 1.0] */
    private final double confidence;

    /** 所有意图类别的评分明细 */
    private final Map<QueryIntent, Double> scoreBreakdown;

    public IntentResult(QueryIntent intent, double confidence, Map<QueryIntent, Double> scoreBreakdown) {
        this.intent = intent;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.scoreBreakdown = scoreBreakdown != null
                ? new HashMap<>(scoreBreakdown)
                : new HashMap<>();
    }

    /**
     * 创建一个仅包含优胜意图的简单结果。
     */
    public static IntentResult of(QueryIntent intent, double confidence) {
        Map<QueryIntent, Double> breakdown = new HashMap<>();
        breakdown.put(intent, confidence);
        return new IntentResult(intent, confidence, breakdown);
    }

    /**
     * 创建一个低置信度的闲聊结果。
     */
    public static IntentResult chitchat() {
        return of(QueryIntent.CHITCHAT, 0.5);
    }

    public QueryIntent getIntent() {
        return intent;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<QueryIntent, Double> getScoreBreakdown() {
        return Collections.unmodifiableMap(scoreBreakdown);
    }

    public double getScore(QueryIntent intentType) {
        return scoreBreakdown.getOrDefault(intentType, 0.0);
    }

    @Override
    public String toString() {
        return "IntentResult{intent=" + intent
                + ", confidence=" + String.format("%.2f", confidence)
                + ", scores=" + scoreBreakdown + "}";
    }
}
