package com.luutry.agent.common.constant;

/**
 * Agent全局常量定义
 * <p>
 * 用途：集中管理Agent系统运行所需的全局配置常量，避免硬编码魔法值。
 * 处理问题：各模块散落配置数值（如最大轮次、Token上限），修改困难且容易遗漏。
 * 处理方式：定义为static final常量，所有模块统一引用；后续可迁移至application.yml动态配置。
 * </p>
 */
public final class AgentConstants {

    private AgentConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 单次Agent任务中Tool/RAG调用的最大轮次，
     * 防止无限循环调用
     */
    public static final int MAX_TOOL_ROUNDS = 10;

    /**
     * 上下文聚合的最大Token总预算，
     * 防止LLM请求时Token爆炸
     */
    public static final int MAX_TOKEN_BUDGET = 4096;

    /**
     * 工具执行的默认超时时间（毫秒）
     */
    public static final long DEFAULT_TOOL_TIMEOUT_MS = 30_000L;

    /**
     * 工具执行遇到临时失败时的默认重试次数
     */
    public static final int DEFAULT_TOOL_RETRY_COUNT = 2;

    /**
     * 用户输入Query的最大长度，超出将被拒绝
     */
    public static final int MAX_QUERY_LENGTH = 2000;

    /**
     * 分页查询的默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 分页查询的最大每页大小
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 记忆模块配置 ====================

    /**
     * 默认会话TTL（毫秒，30分钟）。
     * 短期记忆会话在此不活跃时长后过期。
     */
    public static final long DEFAULT_SESSION_TTL_MS = 30 * 60 * 1000L;

    /**
     * 单个会话保留的最大轮次数。
     * 超出部分从最旧开始丢弃，防止超长会话把 Redis 内存撑爆。
     */
    public static final int MAX_TURNS_PER_SESSION = 50;

    /**
     * 对话摘要压缩的滑动窗口大小。
     * 当对话轮次超过此数量时，较早的轮次将被压缩为摘要。
     */
    public static final int SUMMARY_WINDOW_SIZE = 6;

    /**
     * 对话摘要的最大字符长度。
     * 防止摘要文本消耗过多Token。
     */
    public static final int MAX_SUMMARY_LENGTH = 500;

    /**
     * 长期记忆TTL（毫秒，7天）。
     * 记忆在此时长后过期，除非被频繁访问。
     */
    public static final long LONG_TERM_MEMORY_TTL_MS = 7L * 24 * 3600 * 1000;

    /**
     * 记忆遗忘的衰减分数阈值。
     * 衰减周期中decayScore低于此值的记忆将被移除。
     */
    public static final double MEMORY_DECAY_THRESHOLD = 0.1;

    /**
     * 记忆衰减的指数时间衰减系数λ。
     * 值越大遗忘越快。公式：e^(-λ × 距上次访问小时数)
     */
    public static final double MEMORY_DECAY_LAMBDA = 0.05;

    /**
     * 记忆衰减扫描间隔（毫秒，10分钟）。
     * 后台调度器按此频率执行衰减周期。
     */
    public static final long MEMORY_DECAY_INTERVAL_MS = 10 * 60 * 1000L;

    /**
     * 会话上下文检索的最大近期对话轮次数
     */
    public static final int MAX_RECENT_TURNS = 10;

    /**
     * 记忆语义检索的默认Top-K数量
     */
    public static final int DEFAULT_MEMORY_SEARCH_TOP_K = 5;

    // ==================== RAG检索模块配置 ====================

    /**
     * 单个文本Chunk的最大字符长度。
     * 超过此长度的Chunk将按段落拆分。
     */
    public static final int RAG_CHUNK_MAX_CHARS = 500;

    /**
     * 相邻Chunk之间的重叠字符数，保证上下文连续性
     */
    public static final int RAG_CHUNK_OVERLAP_CHARS = 50;

    /**
     * BM25词频饱和参数（k1）。
     * 控制词频饱和达到的速度。
     */
    public static final double RAG_BM25_K1 = 1.2;

    /**
     * BM25文档长度归一化参数（b）。
     * 0.0 = 不归一化，1.0 = 完全归一化。
     */
    public static final double RAG_BM25_B = 0.75;

    /**
     * 倒数排名融合常数（k）。
     * 用于混合检索：score = 1/(k + rank)。
     */
    public static final double RAG_RRF_K = 60.0;

    /**
     * RAG检索结果的Token预算。
     * 所有召回Chunk的总Token数不得超过此值。
     */
    public static final int RAG_TOKEN_BUDGET = 1500;

    /**
     * RAG检索的默认返回结果数量
     */
    public static final int RAG_DEFAULT_TOP_K = 10;

    /**
     * Chunk去重的Jaccard相似度阈值。
     * 相似度高于此值的Chunk被视为重复。
     */
    public static final double RAG_DEDUP_THRESHOLD = 0.8;

    // ==================== 预处理器配置 ====================

    /**
     * 意图分类的置信度阈值。
     * 如果最高意图得分低于此值，该Query被视为低置信度。
     */
    public static final double INTENT_CONFIDENCE_THRESHOLD = 0.6;

    /**
     * 复合Query拆解的最大子任务数量。
     * 防止过度任务拆分。
     */
    public static final int MAX_SUB_TASK_COUNT = 5;

    /**
     * 指代消解时回溯的近期对话轮次数
     */
    public static final int COREFERENCE_HISTORY_WINDOW = 3;

    /**
     * 查询清洗硬截断倍数。
     * 硬截断上限 = MAX_QUERY_LENGTH × 此倍数，
     * 在所有正则操作之前执行，防止超长输入导致 ReDoS CPU 放大。
     */
    public static final int HARD_LIMIT_MULTIPLIER = 4;

    // ==================== Planner规划器配置 ====================

    /**
     * 每个任务强制终止前的最大ReAct轮次。
     * 防止Tool/RAG调用陷入无限循环。
     */
    public static final int PLANNER_MAX_ROUNDS = 10;

    /**
     * 复合Query拆解的最大子任务数量
     */
    public static final int PLANNER_MAX_SUBTASKS = 5;

    // ==================== 上下文聚合器配置 ====================

    /**
     * 发送给LLM的聚合上下文Token预算。
     * 所有上下文片段的总Token数不得超过此值。
     */
    public static final int CONTEXT_TOKEN_BUDGET = 4096;

    /**
     * 上下文片段去重的Jaccard相似度阈值。
     * 相似度高于此值的片段被视为重复。
     */
    public static final double CONTEXT_DEDUP_THRESHOLD = 0.85;

    /**
     * 聚合输出中上下文片段的最大数量。
     * 即使在Token预算内，也防止片段数量过多。
     */
    public static final int CONTEXT_MAX_SEGMENTS = 50;

    /**
     * 中文字符的Token估算比例。
     * 中文字符比英文字母消耗更多Token。
     * 公式：中文Token数 = 中文字符数 × 比例
     */
    public static final double CONTEXT_CHINESE_TOKEN_RATIO = 1.5;

    // ==================== 反思校验层配置 ====================

    /**
     * 幻觉分数阈值，低于此值则拒绝回答。
     * 如果回答中被上下文支持的句子比例低于此值，则拒绝。
     */
    public static final double CRITIQUE_HALLUCINATION_THRESHOLD = 0.5;

    /**
     * 数值冲突检测阈值。
     * 如果冲突数值之间的比率超过此值，则标记为冲突。
     */
    public static final double CRITIQUE_CONFLICT_THRESHOLD = 0.7;
}
