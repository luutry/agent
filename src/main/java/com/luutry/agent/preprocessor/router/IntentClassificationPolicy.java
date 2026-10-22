package com.luutry.agent.preprocessor.router;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图分类策略配置（V3 Embedding + 线性头）
 * <p>
 * 用途：集中管理 Embedding 意图分类模块的所有可调参数，
 * 由 application.yml 中 agent.intent-classification 段驱动。
 * </p>
 * <p>
 * 设计理念（V3 相比 V2 的根本转变）：
 * V2 使用关键词投票（哪类词出现得多 → 哪类胜出），根本缺陷是意图的本质不是
 * "哪类词多"而是"答案依赖什么外部源"。共享关键词导致系统性误判。
 * V3 改为 Embedding + 线性头：查询文本 → 向量 → Wx+b → softmax → 概率分布。
 * 让模型从语义空间中学"答案在哪里"，而非人工编规则猜。
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.intent-classification")
public class IntentClassificationPolicy {

    // ==================== Embedding 模型配置 ====================

    /**
     * Embedding 模型名称（对应 DashScope API 的 model 参数）。
     */
    private String embeddingModel = "text-embedding-v3";

    /**
     * Embedding 向量维度（text-embedding-v3 默认 1024）。
     * 用于初始化权重矩阵时校验维度一致性。
     */
    private int embeddingDim = 1024;

    // ==================== 决策阈值 ====================

    /**
     * 置信度阈值。最高 softmax 概率低于此值时视为不确定，
     * 降级为 CHITCHAT（低置信度）。
     */
    private double confidenceThreshold = 0.5;

    /**
     * MIXED 意图判定规则：TOOL 和 RAG 的 softmax 概率均 ≥ 此值时判定为 MIXED。
     */
    private double mixedThreshold = 0.3;

    // ==================== 降级配置 ====================

    /**
     * Embedding 调用超时（毫秒）。超时后降级到关键词分类器。
     */
    private long embeddingTimeoutMs = 2000;

    /**
     * 降级关键词列表 — TOOL 意图（仅当 Embedding 调用失败时启用）。
     */
    private List<String> fallbackToolKeywords = List.of(
            "员工", "人员", "团队", "部门", "绩效", "考勤",
            "入职", "离职", "职级", "编制", "简历", "档案",
            "花名册", "司龄", "转正", "名单", "统计",
            "人数", "盘点", "看板", "指标", "数据",
            "查询", "查看", "搜索", "筛选", "列表",
            "评分", "考核结果", "绩效等级", "人员清单",
            "流失率", "到岗率", "转正率", "在职",
            "哪些人", "几个人"
    );

    /**
     * 降级关键词列表 — RAG 意图。
     */
    private List<String> fallbackRagKeywords = List.of(
            "制度", "规定", "流程", "怎么", "如何",
            "什么", "为什么", "政策", "规则", "办法",
            "说明", "解释", "介绍", "条款", "细则",
            "标准", "要求", "条件", "资格", "申请",
            "休假", "请假", "加班", "出差", "报销",
            "晋升条件", "调岗流程", "离职手续", "入职流程"
    );

    /**
     * 降级关键词列表 — CHITCHAT 意图。
     * 注意：不包含"是/对/嗯/啊/哦"等高频单字，防止污染评分。
     */
    private List<String> fallbackChitchatKeywords = List.of(
            "你好", "hello", "hi", "嗨", "早上好", "下午好",
            "谢谢", "感谢", "再见", "拜拜", "ok", "好的"
    );

    // ==================== 种子训练数据 ====================

    /**
     * TOOL 意图种子样本（查询文本 + 预计算向量）。
     * 用于冷启动时的余弦相似度分类，以及离线训练权重矩阵。
     */
    private List<SeedSample> toolSeeds = new ArrayList<>();

    /** RAG 意图种子样本 */
    private List<SeedSample> ragSeeds = new ArrayList<>();

    /** MIXED 意图种子样本 */
    private List<SeedSample> mixedSeeds = new ArrayList<>();

    /** CHITCHAT 意图种子样本 */
    private List<SeedSample> chitchatSeeds = new ArrayList<>();

    /**
     * 种子样本数据结构：包含原始查询文本和预计算的 Embedding 向量。
     * 向量在应用启动时由 EmbeddingService 批量计算并缓存。
     */
    @Getter
    @Setter
    public static class SeedSample {
        /** 原始查询文本 */
        private String query;
        /** 预计算的 Embedding 向量（启动时填充） */
        private float[] embedding;

        public SeedSample() {}

        public SeedSample(String query) {
            this.query = query;
        }
    }
}
