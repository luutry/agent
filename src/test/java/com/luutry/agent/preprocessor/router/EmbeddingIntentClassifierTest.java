package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.rag.vectorstore.EmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EmbeddingIntentClassifier V3 单元测试
 * <p>
 * 覆盖场景：
 * 1. 空值/空白输入 → CHITCHAT
 * 2. Embedding 调用失败 → 降级到关键词分类器（Agent 铁律 #1）
 * 3. 种子余弦相似度路径（无权重时）→ 正确分类
 * 4. 线性头路径（有权重时）→ 正确分类
 * 5. MIXED 意图检测 → 双高概率触发
 * 6. 低置信度 → 阈值以下判定 CHITCHAT
 * </p>
 */
class EmbeddingIntentClassifierTest {

    private static final int TEST_DIM = 4;

    // ==================== 基础输入测试 ====================

    @Nested
    @DisplayName("基础输入处理")
    class BasicInputTests {

        @Test
        void classify_nullInput_returnsChitchat() {
            EmbeddingIntentClassifier classifier = createClassifier();
            IntentResult result = classifier.classify(null);
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
        }

        @Test
        void classify_blankInput_returnsChitchat() {
            EmbeddingIntentClassifier classifier = createClassifier();
            IntentResult result = classifier.classify("   ");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
        }
    }

    // ==================== 降级路径测试 ====================

    @Nested
    @DisplayName("Embedding 失败降级到关键词分类器")
    class FallbackTests {

        @Test
        void classify_embeddingThrows_fallbackToKeyword() {
            EmbeddingService mockService = mock(EmbeddingService.class);
            when(mockService.embed(anyString())).thenThrow(
                    new EmbeddingService.EmbeddingException("模拟异常"));

            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            // "查看员工绩效" → 关键词分类器命中 TOOL（查看/员工/绩效 = 3 hits）
            IntentResult result = classifier.classify("查看员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
        }

        @Test
        void classify_embeddingReturnsNull_fallbackToKeyword() {
            EmbeddingService mockService = mock(EmbeddingService.class);
            when(mockService.embed(anyString())).thenReturn(null);

            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("查看员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
        }
    }

    // ==================== 种子余弦相似度路径测试 ====================

    @Nested
    @DisplayName("种子余弦相似度分类（冷启动路径）")
    class SeedSimilarityTests {

        @Test
        void classify_seedPath_toolQuery() {
            // 查询 "查看员工绩效" → embed 返回 [1,0,0,0]
            // TOOL 种子 "工具查询" → embed 返回 [1,0,0,0] → cosine=1.0
            // RAG 种子 "制度查询" → embed 返回 [0,1,0,0] → cosine=0.0
            // → TOOL 概率最高
            EmbeddingService mockService = createSeedMockService();
            IntentClassificationPolicy policy = createSeedPolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("查看员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertTrue(result.getConfidence() > 0.4, "TOOL 种子完全匹配，置信度应高于阈值");
        }

        @Test
        void classify_seedPath_ragQuery() {
            // 查询 "绩效制度是什么" → embed 返回 [0,1,0,0]
            // TOOL 种子 → [1,0,0,0] → cosine=0.0
            // RAG 种子 → [0,1,0,0] → cosine=1.0
            // → RAG 概率最高
            EmbeddingService mockService = createSeedMockService();
            IntentClassificationPolicy policy = createSeedPolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("绩效制度是什么");
            assertEquals(QueryIntent.RAG, result.getIntent());
            assertTrue(result.getConfidence() > 0.4, "RAG 种子完全匹配，置信度应高于阈值");
        }

        @Test
        void classify_seedPath_mixedDetection() {
            // 查询向量 [0.7, 0.7, 0, 0] → 与 TOOL 种子 [1,0,0,0] 和 RAG 种子 [0,1,0,0]
            // 的余弦相似度接近 → softmax 后 TOOL 和 RAG 概率均 >= mixedThreshold(0.3) → MIXED
            EmbeddingService mockService = createSeedMockService();
            IntentClassificationPolicy policy = createSeedPolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("查看员工绩效并对比制度");
            assertEquals(QueryIntent.MIXED, result.getIntent(),
                    "查询向量同时接近 TOOL 和 RAG 种子 → 应判定 MIXED");
        }

        @Test
        void classify_seedPath_lowConfidence() {
            // 查询向量 [0.1, 0.1, 0.1, 0.1] → 与所有种子余弦相似度都很低
            // softmax 后最大概率 < confidenceThreshold(0.5) → CHITCHAT(低置信度)
            EmbeddingService mockService = createSeedMockService();
            IntentClassificationPolicy policy = createSeedPolicy();
            IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("今天天气不错");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
            assertTrue(result.getConfidence() < 0.5, "低相似度 → 低置信度");
        }
    }

    // ==================== 线性头路径测试 ====================

    @Nested
    @DisplayName("线性头分类（权重矩阵路径）")
    class LinearHeadTests {

        @Test
        void classify_linearHead_toolWins() {
            // 查询 [1,0,0,0]，权重 I（单位矩阵），bias=[0,0,0,0]
            // logits = [1,0,0,0] → softmax ≈ [0.73, 0.09, 0.09, 0.09]
            // → TOOL 胜出
            EmbeddingService mockService = createFixedVectorService(new float[]{2, 0, 0, 0});
            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = new IntentWeightsLoader(createIdentityWeights(), List.of(0.0, 0.0, 0.0, 0.0));
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("查看员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertTrue(result.getConfidence() > 0.6);
        }

        @Test
        void classify_linearHead_ragWins() {
            // 查询 [0,1,0,0]，权重 I
            // logits = [0,1,0,0] → softmax ≈ [0.09, 0.73, 0.09, 0.09]
            // → RAG 胜出
            EmbeddingService mockService = createFixedVectorService(new float[]{0, 2, 0, 0});
            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = new IntentWeightsLoader(createIdentityWeights(), List.of(0.0, 0.0, 0.0, 0.0));
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("绩效制度是什么");
            assertEquals(QueryIntent.RAG, result.getIntent());
            assertTrue(result.getConfidence() > 0.6);
        }

        @Test
        void classify_linearHead_lowConfidence() {
            // 查询 [0.1, 0.1, 0.1, 0.1]，权重 I
            // logits = [0.1, 0.1, 0.1, 0.1] → softmax = [0.25, 0.25, 0.25, 0.25]
            // → 所有类别均 < confidenceThreshold(0.5) → CHITCHAT(低置信度)
            EmbeddingService mockService = createFixedVectorService(new float[]{0.1f, 0.1f, 0.1f, 0.1f});
            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = new IntentWeightsLoader(createIdentityWeights(), List.of(0.0, 0.0, 0.0, 0.0));
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("随便聊聊");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
            assertTrue(result.getConfidence() < 0.3, "均匀分布 → 低置信度");
        }

        @Test
        void classify_linearHead_withBias() {
            // 查询 [0,0,0,0]，权重 I，bias=[10,0,0,0]
            // logits = [10,0,0,0] → softmax ≈ [0.999, ...]
            // → 偏置使 TOOL 即使零向量也能胜出
            EmbeddingService mockService = createFixedVectorService(new float[]{0, 0, 0, 0});
            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = new IntentWeightsLoader(createIdentityWeights(), List.of(10.0, 0.0, 0.0, 0.0));
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("任意查询");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertTrue(result.getConfidence() > 0.99);
        }
    }

    // ==================== 评分明细测试 ====================

    @Nested
    @DisplayName("评分明细完整性")
    class ScoreBreakdownTests {

        @Test
        void classify_linearHead_allScoresPresent() {
            EmbeddingService mockService = createFixedVectorService(new float[]{1, 0, 0, 0});
            IntentClassificationPolicy policy = createBasePolicy();
            IntentWeightsLoader weightsLoader = new IntentWeightsLoader(createIdentityWeights(), List.of(0.0, 0.0, 0.0, 0.0));
            KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
            EmbeddingIntentClassifier classifier =
                    new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);

            IntentResult result = classifier.classify("查看员工绩效");
            assertNotNull(result.getScoreBreakdown());
            assertTrue(result.getScore(QueryIntent.TOOL) >= 0);
            assertTrue(result.getScore(QueryIntent.RAG) >= 0);
            assertTrue(result.getScore(QueryIntent.MIXED) >= 0);
            assertTrue(result.getScore(QueryIntent.CHITCHAT) >= 0);
        }
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建基础 Policy（无种子、无权重，仅降级关键词）
     */
    private IntentClassificationPolicy createBasePolicy() {
        IntentClassificationPolicy policy = new IntentClassificationPolicy();
        policy.setEmbeddingDim(TEST_DIM);
        policy.setConfidenceThreshold(0.4);
        policy.setMixedThreshold(0.3);
        policy.setEmbeddingTimeoutMs(2000);
        policy.setToolSeeds(new ArrayList<>());
        policy.setRagSeeds(new ArrayList<>());
        policy.setMixedSeeds(new ArrayList<>());
        policy.setChitchatSeeds(new ArrayList<>());
        return policy;
    }

    /**
     * 创建带种子数据的 Policy（冷启动路径测试用）
     */
    private IntentClassificationPolicy createSeedPolicy() {
        IntentClassificationPolicy policy = createBasePolicy();
        IntentClassificationPolicy.SeedSample toolSeed = new IntentClassificationPolicy.SeedSample("工具查询");
        IntentClassificationPolicy.SeedSample ragSeed = new IntentClassificationPolicy.SeedSample("制度查询");
        IntentClassificationPolicy.SeedSample chitchatSeed = new IntentClassificationPolicy.SeedSample("闲聊");
        policy.setToolSeeds(List.of(toolSeed));
        policy.setRagSeeds(List.of(ragSeed));
        policy.setChitchatSeeds(List.of(chitchatSeed));
        return policy;
    }

    /**
     * 创建种子路径专用的 EmbeddingService 实现（使用 Map 路由，避免 Mockito anyString 覆盖问题）
     */
    private EmbeddingService createSeedMockService() {
        Map<String, float[]> routes = new HashMap<>();
        routes.put("查看员工绩效", new float[]{1, 0, 0, 0});
        routes.put("绩效制度是什么", new float[]{0, 1, 0, 0});
        routes.put("查看员工绩效并对比制度", new float[]{0.7f, 0.7f, 0, 0});
        routes.put("今天天气不错", new float[]{0.1f, 0.1f, 0.1f, 0.1f});
        routes.put("工具查询", new float[]{1, 0, 0, 0});
        routes.put("制度查询", new float[]{0, 1, 0, 0});
        routes.put("闲聊", new float[]{0, 0, 0, 1});
        float[] defaultVec = new float[]{0.25f, 0.25f, 0.25f, 0.25f};
        return text -> routes.getOrDefault(text, defaultVec);
    }

    /**
     * 创建返回固定向量的 EmbeddingService（用于线性头测试）
     */
    private EmbeddingService createFixedVectorService(float[] fixedVector) {
        return text -> fixedVector;
    }

    /**
     * 创建关键词降级分类器
     */
    private KeywordIntentClassifier createKeywordFallback(IntentClassificationPolicy policy) {
        return new KeywordIntentClassifier(
                policy.getFallbackToolKeywords(),
                policy.getFallbackRagKeywords(),
                policy.getFallbackChitchatKeywords()
        );
    }

    /**
     * 创建分类器（无种子、无权重 → 直接走降级）
     */
    private EmbeddingIntentClassifier createClassifier() {
        EmbeddingService mockService = mock(EmbeddingService.class);
        IntentClassificationPolicy policy = createBasePolicy();
        IntentWeightsLoader weightsLoader = createEmptyWeightsLoader();
        KeywordIntentClassifier keywordFallback = createKeywordFallback(policy);
        return new EmbeddingIntentClassifier(mockService, policy, weightsLoader, keywordFallback);
    }

    /**
     * 创建 4x4 单位矩阵权重（用于线性头测试）
     */
    private List<List<Double>> createIdentityWeights() {
        return List.of(
                List.of(1.0, 0.0, 0.0, 0.0),
                List.of(0.0, 1.0, 0.0, 0.0),
                List.of(0.0, 0.0, 1.0, 0.0),
                List.of(0.0, 0.0, 0.0, 1.0)
        );
    }

    /**
     * 创建空权重加载器（用于无权重路径测试）
     */
    private IntentWeightsLoader createEmptyWeightsLoader() {
        return new IntentWeightsLoader(new ArrayList<>(), List.of(0.0, 0.0, 0.0, 0.0));
    }
}
