package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.rag.vectorstore.EmbeddingService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 基于 Embedding + 线性头的意图分类器（V3）。
 * <p>
 * 用途：将用户查询通过 Embedding 模型编码为语义向量，
 * 再经线性分类头（Wx + b → softmax）输出四类意图概率分布。
 * </p>
 * <p>
 * 核心设计（相比 V2 关键词投票的根本转变）：
 * V2 统计"哪类关键词出现得多"，但意图的本质是"答案依赖什么外部源"。
 * V3 让 Embedding 编码完整语义，线性头学习"查询语义 → 依赖源"的决策边界。
 * "绩效怎么算"（问制度→RAG）和"看看张三绩效"（要数据→TOOL）在 embedding 空间
 * 天然不同，线性头只需学到一个超平面把它们分开。
 * </p>
 * <p>
 * 三级降级策略（Agent 铁律 #1：最外层 catch Throwable，失败降级）：
 * 1. 权重矩阵可用 → 线性头分类（Wx+b → softmax）
 * 2. 权重不可用但种子数据可用 → 余弦相似度分类
 * 3. Embedding 调用失败 → 降级到 KeywordIntentClassifier（关键词兜底）
 * </p>
 */
@Slf4j
public class EmbeddingIntentClassifier implements IntentClassifier {

    /** 意图类别数量：TOOL / RAG / MIXED / CHITCHAT */
    private static final int NUM_CLASSES = 4;

    /** 意图类别与数组索引的映射 */
    private static final QueryIntent[] CLASS_INDEX = {
            QueryIntent.TOOL, QueryIntent.RAG, QueryIntent.MIXED, QueryIntent.CHITCHAT
    };

    private final EmbeddingService embeddingService;
    private final IntentClassificationPolicy policy;
    private final IntentWeightsLoader weightsLoader;
    private final KeywordIntentClassifier fallbackClassifier;

    /** 缓存的种子样本 Embedding（启动时计算，避免重复调用） */
    private volatile boolean seedsEmbedded = false;

    public EmbeddingIntentClassifier(EmbeddingService embeddingService,
                                     IntentClassificationPolicy policy,
                                     IntentWeightsLoader weightsLoader,
                                     KeywordIntentClassifier fallbackClassifier) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.weightsLoader = Objects.requireNonNull(weightsLoader, "weightsLoader must not be null");
        this.fallbackClassifier = Objects.requireNonNull(fallbackClassifier, "fallbackClassifier must not be null");
    }

    @Override
    public IntentResult classify(String query) {
        // 步骤0：空值/空白快速返回
        if (query == null || query.isBlank()) {
            return IntentResult.chitchat();
        }

        try {
            // 步骤1：调用 Embedding 服务生成查询向量（带超时控制）
            float[] queryEmbedding = embedWithTimeout(query);
            if (queryEmbedding == null) {
                log.warn("Embedding 返回 null，降级到关键词分类器: query={}", truncate(query));
                return fallbackClassifier.classify(query);
            }

            // 步骤2：确保种子向量已计算（懒初始化，仅首次调用触发）
            ensureSeedsEmbedded();

            // 步骤3：根据可用资源选择分类路径
            if (hasWeights()) {
                log.debug("分类路径：线性头（权重矩阵）");
                return classifyByLinearHead(queryEmbedding, query);
            } else if (hasSeeds()) {
                log.debug("分类路径：种子余弦相似度");
                return classifyBySeedSimilarity(queryEmbedding, query);
            } else {
                log.warn("无权重且无种子数据，降级到关键词分类器");
                return fallbackClassifier.classify(query);
            }

        } catch (Throwable t) {
            // Agent 铁律 #1：最外层 catch Throwable，降级返回关键词分类结果
            log.error("Embedding 分类器异常，降级到关键词分类器: {}", t.getMessage(), t);
            return fallbackClassifier.classify(query);
        }
    }

    // ==================== 线性头分类路径 ====================

    /**
     * 线性分类头：score = W·embedding + b → softmax → 概率分布。
     * <p>
     * 权重矩阵 W 维度 [4][embeddingDim]，行顺序对应 [TOOL, RAG, MIXED, CHITCHAT]。
     * 偏置向量 b 维度 [4]。
     * </p>
     */
    private IntentResult classifyByLinearHead(float[] embedding, String query) {
        List<List<Double>> weights = weightsLoader.getWeights();
        List<Double> bias = weightsLoader.getBias();

        // 计算各类别的线性得分
        double[] logits = new double[NUM_CLASSES];
        for (int c = 0; c < NUM_CLASSES; c++) {
            List<Double> wRow = weights.get(c);
            double score = (c < bias.size()) ? bias.get(c) : 0.0;
            for (int i = 0; i < embedding.length && i < wRow.size(); i++) {
                score += wRow.get(i) * embedding[i];
            }
            logits[c] = score;
        }

        // softmax 归一化为概率分布
        double[] probs = softmax(logits);
        return buildResultFromProbs(probs, query, "LINEAR_HEAD");
    }

    // ==================== 种子相似度分类路径 ====================

    /**
     * 余弦相似度分类：当权重矩阵不可用时，
     * 计算查询向量与各类别种子样本的余弦相似度，取平均后 softmax。
     */
    private IntentResult classifyBySeedSimilarity(float[] queryEmbedding, String query) {
        double[] classSims = new double[NUM_CLASSES];

        classSims[0] = avgCosineSimilarity(queryEmbedding, policy.getToolSeeds());
        classSims[1] = avgCosineSimilarity(queryEmbedding, policy.getRagSeeds());
        classSims[2] = avgCosineSimilarity(queryEmbedding, policy.getMixedSeeds());
        classSims[3] = avgCosineSimilarity(queryEmbedding, policy.getChitchatSeeds());

        double[] probs = softmax(classSims);
        return buildResultFromProbs(probs, query, "SEED_SIMILARITY");
    }

    // ==================== 公共工具方法 ====================

    /**
     * 根据概率分布构建 IntentResult，包含 MIXED 判定和置信度阈值检查。
     */
    private IntentResult buildResultFromProbs(double[] probs, String query, String method) {
        double toolProb = probs[0];
        double ragProb = probs[1];
        double mixedProb = probs[2];
        double chitchatProb = probs[3];
    
        // 构建评分明细
        Map<QueryIntent, Double> scores = new HashMap<>();
        scores.put(QueryIntent.TOOL, toolProb);
        scores.put(QueryIntent.RAG, ragProb);
        scores.put(QueryIntent.MIXED, mixedProb);
        scores.put(QueryIntent.CHITCHAT, chitchatProb);
    
        // 诊断日志：输出概率分布（INFO 级别，便于排查）
        log.info("意图分类概率 [{}]: query='{}', T={}, R={}, M={}, C={}",
                method, truncate(query), 
                String.format("%.3f", toolProb),
                String.format("%.3f", ragProb),
                String.format("%.3f", mixedProb),
                String.format("%.3f", chitchatProb));
    
        // MIXED 判定：TOOL 和 RAG 概率均 ≥ mixedThreshold
        if (toolProb >= policy.getMixedThreshold() && ragProb >= policy.getMixedThreshold()) {
            log.debug("意图分类 [{}] → MIXED", method);
            return new IntentResult(QueryIntent.MIXED, mixedProb, scores);
        }
    
        // 取概率最大的类别
        int bestIdx = argMax(probs);
        QueryIntent bestIntent = CLASS_INDEX[bestIdx];
        double confidence = probs[bestIdx];
    
        // 置信度阈值检查：低于阈值视为不确定，降级为低置信度 CHITCHAT
        if (confidence < policy.getConfidenceThreshold()) {
            log.info("意图分类 [{}] → CHITCHAT(低置信度 {}  < 阈值 {})",
                    method, String.format("%.3f", confidence), policy.getConfidenceThreshold());
            return new IntentResult(QueryIntent.CHITCHAT, confidence, scores);
        }
    
        log.info("意图分类 [{}] → {} (置信度 {})", method, bestIntent, String.format("%.3f", confidence));
        return new IntentResult(bestIntent, confidence, scores);
    }

    /**
     * 带超时控制的 Embedding 调用。
     * 直接调用 Embedding 服务，异常时返回 null 触发降级。
     * <p>
     * TODO: 生产环境增加虚拟线程超时控制，当前简化为直接调用以便测试验证。
     * </p>
     */
    private float[] embedWithTimeout(String query) {
        try {
            return embeddingService.embed(query);
        } catch (Throwable t) {
            log.warn("Embedding 调用异常: {}", t.getMessage());
            return null;
        }
    }

    /**
     * 懒初始化种子样本的 Embedding 向量。
     * 仅在首次 classify 调用时触发，后续调用直接命中缓存。
     * 无论成功或失败都标记 seedsEmbedded=true，防止失败后反复重试（雪崩防护）。
     */
    private synchronized void ensureSeedsEmbedded() {
        if (seedsEmbedded) {
            return;
        }
        try {
            embedSeedList(policy.getToolSeeds());
            embedSeedList(policy.getRagSeeds());
            embedSeedList(policy.getMixedSeeds());
            embedSeedList(policy.getChitchatSeeds());
                
            // 统计实际成功计算 embedding 的种子数量（诊断用）
            long toolEmbedded = countEmbeddedSeeds(policy.getToolSeeds());
            long ragEmbedded = countEmbeddedSeeds(policy.getRagSeeds());
            long mixedEmbedded = countEmbeddedSeeds(policy.getMixedSeeds());
            long chitchatEmbedded = countEmbeddedSeeds(policy.getChitchatSeeds());
                
            log.info("种子样本 Embedding 初始化完成: tool={}/{}，rag={}/{}，mixed={}/{}，chitchat={}/{}",
                    toolEmbedded, policy.getToolSeeds().size(),
                    ragEmbedded, policy.getRagSeeds().size(),
                    mixedEmbedded, policy.getMixedSeeds().size(),
                    chitchatEmbedded, policy.getChitchatSeeds().size());
                
            // 如果所有种子 embedding 都失败，记录警告
            if (toolEmbedded == 0 && ragEmbedded == 0 && mixedEmbedded == 0 && chitchatEmbedded == 0) {
                log.warn("所有种子 Embedding 计算失败，将降级到关键词分类器");
            }
        } catch (Throwable t) {
            log.warn("种子 Embedding 初始化失败，将使用关键词降级：{}", t.getMessage());
        } finally {
            // 无论成功失败都标记完成，防止失败后每次 classify 都重试导致雪崩
            seedsEmbedded = true;
        }
    }
        
    /**
     * 统计种子列表中成功计算 embedding 的数量（诊断用）。
     */
    private long countEmbeddedSeeds(List<IntentClassificationPolicy.SeedSample> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return 0;
        }
        return seeds.stream().filter(s -> s.getEmbedding() != null).count();
    }

    /**
     * 批量计算种子列表的 Embedding 向量，填充到 SeedSample.embedding 字段。
     */
    private void embedSeedList(List<IntentClassificationPolicy.SeedSample> seeds) {
        for (IntentClassificationPolicy.SeedSample seed : seeds) {
            if (seed.getQuery() != null && !seed.getQuery().isBlank() && seed.getEmbedding() == null) {
                try {
                    seed.setEmbedding(embeddingService.embed(seed.getQuery()));
                } catch (Exception e) {
                    log.warn("种子 Embedding 计算失败: query={}", seed.getQuery());
                }
            }
        }
    }

    // ==================== 数学工具方法 ====================

    /**
     * Softmax 归一化：将任意实数向量转换为概率分布。
     * 减去最大值防止 exp() 溢出（数值稳定性）。
     */
    private double[] softmax(double[] logits) {
        double maxLogit = Double.NEGATIVE_INFINITY;
        for (double l : logits) {
            maxLogit = Math.max(maxLogit, l);
        }
        double[] exps = new double[logits.length];
        double sumExps = 0.0;
        for (int i = 0; i < logits.length; i++) {
            exps[i] = Math.exp(logits[i] - maxLogit);
            sumExps += exps[i];
        }
        if (sumExps == 0.0) {
            // 极端情况：所有 exp 为 0，返回均匀分布
            double uniform = 1.0 / logits.length;
            return Arrays.stream(new double[logits.length]).map(x -> uniform).toArray();
        }
        for (int i = 0; i < exps.length; i++) {
            exps[i] /= sumExps;
        }
        return exps;
    }

    /**
     * 余弦相似度：cos(A, B) = dot(A,B) / (|A| × |B|)。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /**
     * 计算查询向量与种子列表的平均余弦相似度。
     */
    private double avgCosineSimilarity(float[] queryEmb, List<IntentClassificationPolicy.SeedSample> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int validCount = 0;
        for (IntentClassificationPolicy.SeedSample seed : seeds) {
            if (seed.getEmbedding() != null) {
                sum += cosineSimilarity(queryEmb, seed.getEmbedding());
                validCount++;
            }
        }
        return validCount == 0 ? 0.0 : sum / validCount;
    }

    /**
     * 返回数组中最大值的索引。
     */
    private int argMax(double[] arr) {
        int maxIdx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    // ==================== 状态查询方法 ====================

    private boolean hasWeights() {
        return weightsLoader.hasWeights();
    }

    /**
     * 检查种子向量是否真正可用（至少有一个种子完成了 Embedding 计算）。
     * 注意：不能只检查种子列表是否非空——列表有数据但 embedding 全为 null 时，
     * 走种子相似度路径会得到全 0 的余弦相似度，softmax 均匀分布后必然低于阈值，
     * 错误地返回 CHITCHAT。
     */
    private boolean hasSeeds() {
        return hasEmbeddedSeed(policy.getToolSeeds())
                || hasEmbeddedSeed(policy.getRagSeeds())
                || hasEmbeddedSeed(policy.getMixedSeeds())
                || hasEmbeddedSeed(policy.getChitchatSeeds());
    }

    /**
     * 检查种子列表中是否至少有一个已完成 Embedding 计算。
     */
    private boolean hasEmbeddedSeed(List<IntentClassificationPolicy.SeedSample> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return false;
        }
        for (IntentClassificationPolicy.SeedSample seed : seeds) {
            if (seed.getEmbedding() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 截断查询文本用于日志输出，防止日志注入。
     */
    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
