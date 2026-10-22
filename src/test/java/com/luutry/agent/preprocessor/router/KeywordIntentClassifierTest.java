package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.QueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeywordIntentClassifier 降级分类器单元测试
 * <p>
 * 验证关键词分类器作为 Embedding 降级后备的正确性：
 * - TOOL/RAG/CHITCHAT 基本分类
 * - MIXED 判定加严（需 ≥ 2 命中）
 * - 无匹配低置信度
 * - 短词边界保护
 * </p>
 */
class KeywordIntentClassifierTest {

    private KeywordIntentClassifier classifier;

    /** V2 原始 TOOL 关键词（降级用） */
    private static final List<String> TOOL_KEYWORDS = List.of(
            "员工", "人员", "团队", "部门", "绩效", "考勤",
            "入职", "离职", "职级", "编制", "简历", "档案",
            "花名册", "司龄", "转正", "名单", "统计",
            "人数", "盘点", "看板", "指标", "数据",
            "查询", "查看", "搜索", "筛选", "列表",
            "评分", "考核结果", "绩效等级", "人员清单",
            "流失率", "到岗率", "转正率", "在职",
            "哪些人", "几个人"
    );

    /** V2 原始 RAG 关键词（降级用） */
    private static final List<String> RAG_KEYWORDS = List.of(
            "制度", "规定", "流程", "怎么", "如何",
            "什么", "为什么", "政策", "规则", "办法",
            "说明", "解释", "介绍", "条款", "细则",
            "标准", "要求", "条件", "资格", "申请",
            "休假", "请假", "加班", "出差", "报销",
            "晋升条件", "调岗流程", "离职手续", "入职流程"
    );

    /** V2 原始 CHITCHAT 关键词（降级用，不含高频噪声词） */
    private static final List<String> CHITCHAT_KEYWORDS = List.of(
            "你好", "hello", "hi", "嗨", "早上好", "下午好",
            "谢谢", "感谢", "再见", "拜拜", "ok", "好的"
    );

    @BeforeEach
    void setUp() {
        classifier = new KeywordIntentClassifier(TOOL_KEYWORDS, RAG_KEYWORDS, CHITCHAT_KEYWORDS);
    }

    // ==================== 基本分类测试 ====================

    @Nested
    @DisplayName("基本意图分类")
    class BasicClassificationTests {

        @Test
        void classify_toolIntent_detected() {
            IntentResult result = classifier.classify("查看研发部门员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertTrue(result.getConfidence() > 0);
        }

        @Test
        void classify_ragIntent_detected() {
            IntentResult result = classifier.classify("离职流程是怎么规定的");
            assertEquals(QueryIntent.RAG, result.getIntent());
            assertTrue(result.getConfidence() > 0);
        }

        @Test
        void classify_chitchat_detected() {
            IntentResult result = classifier.classify("你好");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
            assertTrue(result.getConfidence() > 0);
        }

        @Test
        void classify_nullInput_returnsChitchat() {
            IntentResult result = classifier.classify(null);
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
        }

        @Test
        void classify_blankInput_returnsChitchat() {
            IntentResult result = classifier.classify("   ");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
        }
    }

    // ==================== MIXED 判定测试 ====================

    @Nested
    @DisplayName("MIXED 意图判定")
    class MixedTests {

        @Test
        void classify_oneToolOneRag_notMixed() {
            // "查看绩效制度" → TOOL: "查看","绩效"=2, RAG: "制度"=1
            // ragHits=1 < MIXED_MIN_HITS=2 → 不是 MIXED
            IntentResult result = classifier.classify("查看绩效制度");
            assertNotEquals(QueryIntent.MIXED, result.getIntent());
        }

        @Test
        void classify_twoToolTwoRag_isMixed() {
            // "查看员工绩效制度和考勤规定" → TOOL: 查看/员工/绩效=3, RAG: 制度/规定=2 (考勤也在TOOL)
            IntentResult result = classifier.classify("查看员工绩效制度和考勤规定");
            assertEquals(QueryIntent.MIXED, result.getIntent());
        }
    }

    // ==================== 置信度测试 ====================

    @Nested
    @DisplayName("置信度计算")
    class ConfidenceTests {

        @Test
        void classify_singleKeyword_confidenceNotOne() {
            // "绩效" → 只命中 TOOL 1 个关键词 → confidence = min(1/3.0, 1.0) ≈ 0.333
            IntentResult result = classifier.classify("绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertTrue(result.getConfidence() < 1.0);
            assertTrue(result.getConfidence() > 0.0 && result.getConfidence() <= 0.5);
        }

        @Test
        void classify_threeKeywords_confidenceReachesOne() {
            // "查看员工绩效" → 3 个 TOOL 关键词 → confidence = min(3/3.0, 1.0) = 1.0
            IntentResult result = classifier.classify("查看员工绩效");
            assertEquals(QueryIntent.TOOL, result.getIntent());
            assertEquals(1.0, result.getConfidence(), 0.01);
        }

        @Test
        void classify_noMatch_lowConfidence() {
            IntentResult result = classifier.classify("今天天气不错");
            assertEquals(QueryIntent.CHITCHAT, result.getIntent());
            assertEquals(0.1, result.getConfidence(), 0.01);
        }
    }

    // ==================== 评分明细测试 ====================

    @Nested
    @DisplayName("评分明细")
    class ScoreBreakdownTests {

        @Test
        void classify_scoreBreakdown_populated() {
            IntentResult result = classifier.classify("查看员工绩效");
            assertNotNull(result.getScoreBreakdown());
            assertTrue(result.getScore(QueryIntent.TOOL) > 0);
            assertEquals(0.0, result.getScore(QueryIntent.RAG));
        }

        @Test
        void classify_confidence_withinRange() {
            IntentResult result = classifier.classify("查看员工绩效");
            assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
        }
    }
}
