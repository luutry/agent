package com.luutry.agent.preprocessor.model;

import com.luutry.agent.preprocessor.cleaner.CleanResult;
import com.luutry.agent.preprocessor.resolver.ResolveResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 完整预处理管道后的查询结果。
 * 包含清洗、指代消解、改写后的查询文本，以及意图分类
 * 和复合查询的可选子任务。
 */
public class ProcessedQuery {

    /** 原始用户输入 */
    private final String originalQuery;

    /** 清洗后的查询（移除特殊字符、规范化空白） */
    private String cleanedQuery;

    /** 指代消解后的查询（代词替换为实体） */
    private String resolvedQuery;

    /** 改写后的查询（同义词扩展、术语标准化） */
    private String rewrittenQuery;

    /** 分类后的意图 */
    private QueryIntent intent;

    /** 意图分类置信度分数 [0.0, 1.0] */
    private double confidence;

    /** 提取的实体（员工姓名、部门名称等） */
    private final List<String> entities;

    /** 从复合查询分解出的子任务 */
    private final List<SubTask> subTasks;

    /** 是否为复合（多意图）查询 */
    private boolean composite;

    /** 清洗结果（含截断元信息），供下游根据 truncated/hardRejected 做差异化决策 */
    private CleanResult cleanResult;

    /** 指代消解结果（含消解元信息），供下游根据 resolved/confidence/method 做差异化决策 */
    private ResolveResult resolveResult;

    /** 管道中的独立查询单元（复合判定后产生，每个 unit 独立走 intent/slots/retrieval） */
    private final List<QueryUnit> units;

    public ProcessedQuery(String originalQuery) {
        this.originalQuery = Objects.requireNonNull(originalQuery, "originalQuery must not be null");
        this.entities = new ArrayList<>();
        this.subTasks = new ArrayList<>();
        this.units = new ArrayList<>();
        this.intent = QueryIntent.CHITCHAT;
        this.confidence = 0.0;
        this.cleanedQuery = originalQuery;
        this.resolvedQuery = originalQuery;
        this.rewrittenQuery = originalQuery;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String getCleanedQuery() {
        return cleanedQuery;
    }

    public void setCleanedQuery(String cleanedQuery) {
        this.cleanedQuery = cleanedQuery;
    }

    public String getResolvedQuery() {
        return resolvedQuery;
    }

    public void setResolvedQuery(String resolvedQuery) {
        this.resolvedQuery = resolvedQuery;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery) {
        this.rewrittenQuery = rewrittenQuery;
    }

    /**
     * 返回供下游消费的最终查询。
     * 优先级：resolvedQuery > cleanedQuery
     * （rewrite 已移出管道，rewrittenQuery 不再由管道设置）
     */
    public String getFinalQuery() {
        if (resolvedQuery != null && !resolvedQuery.isBlank()) {
            return resolvedQuery;
        }
        return cleanedQuery;
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

    public List<String> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    public void addEntity(String entity) {
        if (entity != null && !entity.isBlank()) {
            entities.add(entity.trim());
        }
    }

    public List<SubTask> getSubTasks() {
        return Collections.unmodifiableList(subTasks);
    }

    public void addSubTask(SubTask subTask) {
        if (subTask != null) {
            subTasks.add(subTask);
            this.composite = subTasks.size() > 1;
        }
    }

    public boolean isComposite() {
        return composite || units.size() > 1;
    }

    public void setComposite(boolean composite) {
        this.composite = composite;
    }

    public CleanResult getCleanResult() {
        return cleanResult;
    }

    public void setCleanResult(CleanResult cleanResult) {
        this.cleanResult = cleanResult;
    }

    public ResolveResult getResolveResult() {
        return resolveResult;
    }

    public void setResolveResult(ResolveResult resolveResult) {
        this.resolveResult = resolveResult;
    }

    public List<QueryUnit> getUnits() {
        return Collections.unmodifiableList(units);
    }

    public void addUnit(QueryUnit unit) {
        if (unit != null) {
            units.add(unit);
        }
    }

    /**
     * 指代消解是否成功（委托给 ResolveResult）。
     * 下游可据此决定是否使用消解后文本。
     */
    public boolean isCoreferenceResolved() {
        return resolveResult != null && resolveResult.isResolved();
    }

    /**
     * 指代消解置信度（委托给 ResolveResult）。
     * 下游可据此判断消解结果的可靠程度。
     */
    public double getResolveConfidence() {
        return resolveResult != null ? resolveResult.getConfidence() : 1.0;
    }

    /**
     * 查询是否被截断（委托给 CleanResult）。
     * 下游可据此决定是否提示用户"查询过长已截断"。
     */
    public boolean isTruncated() {
        return cleanResult != null && cleanResult.isTruncated();
    }

    /**
     * 查询是否超过 HARD 拒答阈值（委托给 CleanResult）。
     * 上游 Controller 可据此决定是否拒答。
     */
    public boolean isHardRejected() {
        return cleanResult != null && cleanResult.isHardRejected();
    }

    @Override
    public String toString() {
        return "ProcessedQuery{intent=" + intent
                + ", confidence=" + String.format("%.2f", confidence)
                + ", units=" + units.size()
                + ", finalQuery='" + getFinalQuery() + "'}";
    }
}
