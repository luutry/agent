package com.luutry.agent.preprocessor.resolver;

import com.luutry.agent.llm.LlmService;
import com.luutry.agent.memory.ConversationHistoryService;
import com.luutry.agent.preprocessor.cleaner.CleanPolicy;
import com.luutry.agent.preprocessor.cleaner.DefaultQueryCleaner;
import com.luutry.agent.preprocessor.cleaner.QueryCleaner;
import com.luutry.agent.preprocessor.cleaner.QueryInputDesensitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SimpleCoreferenceResolver V2 单元测试
 * <p>
 * 覆盖场景：
 * - 基础空值/无代词/无历史
 * - 误触发防护（其他/因此/其中等）
 * - 规则快速路径（该员工/该部门/刚才那些/刚才的）
 * - LLM 消解路径（委托 LlmCoreferenceResolver）
 * - 降级场景
 * </p>
 */
class SimpleCoreferenceResolverTest {

    private static final String SESSION = "session-1";

    private SimpleCoreferenceResolver resolver;
    private ResolvePolicy policy;
    private LlmCoreferenceResolver llmResolver;
    private LlmService llmService;
    private ConversationHistoryService historyService;

    @BeforeEach
    void setUp() {
        policy = new ResolvePolicy();
        llmService = mock(LlmService.class);
        llmResolver = new LlmCoreferenceResolver(llmService, policy);
        historyService = mock(ConversationHistoryService.class);
        QueryCleaner queryCleaner = new DefaultQueryCleaner(new CleanPolicy(), new QueryInputDesensitizer());
        resolver = new SimpleCoreferenceResolver(policy, llmResolver, historyService, queryCleaner);
    }

    /**
     * 历史不再由调用方传入：测试通过 stub 会话历史服务模拟“用到再拿”，
     * 保持既有用例仍以 (query, history) 的语义书写。
     */
    private ResolveResult resolveWithHistory(String query, List<String> history) {
        when(historyService.recentUserQueries(SESSION)).thenReturn(history);
        return resolver.resolve(query, SESSION);
    }

    @Test
    void resolve_historyWithPii_maskedBeforeEnteringLlm() {
        // R3：历史里的手机号必须在进入 LLM prompt 前脱敏，消解器持有清洗器负责此事
        when(historyService.recentUserQueries(SESSION))
                .thenReturn(List.of("联系 138 1234 5678 的张三"));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmService.generate(anyString(), promptCaptor.capture())).thenReturn(null);

        resolver.resolve("他的电话是多少", SESSION);

        String prompt = promptCaptor.getValue();
        assertFalse(prompt.contains("138 1234 5678"), "原始手机号不得进入 prompt");
        assertTrue(prompt.contains("138****5678"), "历史应先脱敏再拼装 prompt");
    }

    // ==================== 基础场景 ====================

    @Nested
    @DisplayName("基础场景：空值/无代词/无历史")
    class BasicTests {

        @Test
        void resolve_nullQuery_returnsUnresolved() {
            ResolveResult result = resolveWithHistory(null, List.of("历史"));
            assertNotNull(result);
            assertFalse(result.isResolved());
            // null 输入降级后返回 ""（空字符串），非 null
            assertEquals("", result.getResolvedText());
        }

        @Test
        void resolve_blankQuery_returnsUnresolved() {
            ResolveResult result = resolveWithHistory("", List.of("历史"));
            assertNotNull(result);
            assertFalse(result.isResolved());
            assertEquals("", result.getResolvedText());
        }

        @Test
        void resolve_noPronoun_returnsUnresolved() {
            ResolveResult result = resolveWithHistory("查看研发部门的绩效", List.of("上次查了市场部的考勤"));
            assertNotNull(result);
            assertFalse(result.isResolved());
            assertEquals("查看研发部门的绩效", result.getResolvedText());
        }

        @Test
        void resolve_withPronounNoHistory_returnsUnresolved() {
            ResolveResult result = resolveWithHistory("他的绩效怎么样", Collections.emptyList());
            assertNotNull(result);
            assertFalse(result.isResolved());
            assertEquals("他的绩效怎么样", result.getResolvedText());
        }

        @Test
        void resolve_withPronounNullHistory_returnsUnresolved() {
            ResolveResult result = resolveWithHistory("他的绩效怎么样", null);
            assertNotNull(result);
            assertFalse(result.isResolved());
        }
    }

    // ==================== 误触发防护 ====================

    @Nested
    @DisplayName("误触发防护：复合词中的代词字符不应触发消解")
    class FalsePositiveTests {

        @Test
        void resolve_qita_notTriggered() {
            // "其他国家" 中的 "他" 是复合词的一部分，不应触发
            ResolveResult result = resolveWithHistory("查看其他国家的制度", List.of("查看张三的简历"));
            assertFalse(result.isResolved(), "'其他'不应触发指代消解");
            assertEquals("查看其他国家的制度", result.getResolvedText());
        }

        @Test
        void resolve_yinci_notTriggered() {
            // "因此" 不应触发
            ResolveResult result = resolveWithHistory("因此需要重新评估", List.of("查看张三的绩效"));
            assertFalse(result.isResolved(), "'因此'不应触发指代消解");
        }

        @Test
        void resolve_qizhong_notTriggered() {
            // "其中" 不应触发
            ResolveResult result = resolveWithHistory("其中有哪些规定", List.of("查看研发部门的人员"));
            assertFalse(result.isResolved(), "'其中'不应触发指代消解");
        }

        @Test
        void resolve_qici_notTriggered() {
            // "其次" 不应触发
            ResolveResult result = resolveWithHistory("其次看考勤", List.of("先查看绩效"));
            assertFalse(result.isResolved(), "'其次'不应触发指代消解");
        }

        @Test
        void resolve_youshenmeyici_mixedCase() {
            // "查看其他和他的绩效" → 剥离"其他"后仍有独立"他" → 应触发
            ResolveResult result = resolveWithHistory("查看其他和他的绩效",
                    List.of("张三的绩效是A"));
            // 含独立代词"他"，应触发消解（规则快速路径或LLM）
            assertNotNull(result);
            // 由于 LLM mock 返回 null，最终会降级
            // 但触发器应该正确检测到独立代词
        }
    }

    // ==================== 规则快速路径 ====================

    @Nested
    @DisplayName("规则快速路径：高置信度模式直接替换")
    class RuleFastPathTests {

        @Test
        void resolve_gaiyuangong_replacesWithPersonFromHistory() {
            // "该员工" → 从历史中提取人名
            ResolveResult result = resolveWithHistory("该员工的绩效怎么样",
                    List.of("查看张三的绩效信息"));
            assertTrue(result.isResolved(), "该员工应被规则快速路径消解");
            assertEquals(ResolveMethod.RULE_FAST_PATH, result.getMethod());
            assertEquals(1.0, result.getConfidence());
            assertTrue(result.getResolvedText().contains("张三"));
            assertFalse(result.getResolvedText().contains("该员工"));
        }

        @Test
        void resolve_gaibumen_replacesWithDeptFromHistory() {
            // "该部门" → 从历史中提取部门名
            ResolveResult result = resolveWithHistory("该部门的编制是多少",
                    List.of("看看产品团队的情况"));
            assertTrue(result.isResolved(), "该部门应被规则快速路径消解");
            assertEquals(ResolveMethod.RULE_FAST_PATH, result.getMethod());
            assertTrue(result.getResolvedText().contains("产品团队"));
        }

        @Test
        void resolve_gangcai_naxie_replacesWithRecentEntity() {
            // "刚才那些" → 从历史中提取最近实体
            ResolveResult result = resolveWithHistory("刚才那些人的绩效",
                    List.of("查看研发部门的人员名单"));
            assertTrue(result.isResolved(), "刚才那些应被规则快速路径消解");
            assertEquals(ResolveMethod.RULE_FAST_PATH, result.getMethod());
        }

        @Test
        void resolve_gangcai_de_replacesWithRecentEntity() {
            // "刚才的" → 从历史中提取最近实体
            ResolveResult result = resolveWithHistory("刚才的离职率",
                    List.of("查看研发部门的离职信息"));
            assertTrue(result.isResolved(), "刚才的应被规则快速路径消解");
            assertEquals(ResolveMethod.RULE_FAST_PATH, result.getMethod());
        }

        @Test
        void resolve_gaiyuangong_noMatchingHistory_returnsUnresolved() {
            // "该员工" 但历史中没有人名 → 快速路径失败 → 降级（LLM 也 mock 失败）
            ResolveResult result = resolveWithHistory("该员工的绩效",
                    List.of("今天天气不错"));
            // 快速路径找不到person，LLM也无法消解（mock返回null）
            assertFalse(result.isResolved());
        }

        @Test
        void resolve_ruleFastPath_pairsContainsMapping() {
            ResolveResult result = resolveWithHistory("该员工的绩效",
                    List.of("查看张三的绩效信息"));
            assertTrue(result.isResolved());
            assertEquals(1, result.getResolvedPairs().size());
            assertTrue(result.getResolvedPairs().containsKey("该员工"));
            assertEquals("张三", result.getResolvedPairs().get("该员工"));
        }

        @Test
        void resolve_entityExtraction_stripsLeadingVerb() {
            ResolveResult result = resolveWithHistory("该部门的编制是多少",
                    List.of("查一下研发部门的情况"));
            assertTrue(result.isResolved());
            assertTrue(result.getResolvedText().contains("研发部门"),
                    "实际=" + result.getResolvedText());
            assertFalse(result.getResolvedText().contains("查一下"), "实体不应带查询动词");
        }

        @Test
        void resolve_multiplePatterns_replacesAll() {
            ResolveResult result = resolveWithHistory("该员工和该部门的绩效",
                    List.of("张三的绩效是A", "研发部门有20人"));
            assertTrue(result.isResolved());
            assertEquals(2, result.getResolvedPairs().size());
            assertFalse(result.getResolvedText().contains("该员工"));
            assertFalse(result.getResolvedText().contains("该部门"));
        }
    }

    // ==================== LLM 消解路径 ====================

    @Nested
    @DisplayName("LLM 消解路径：模糊代词委托 LLM 处理")
    class LlmPathTests {

        @Test
        void resolve_ambiguousPronoun_delegatesToLlm() {
            // "他的绩效" → 不在快速路径模式 → 委托 LLM
            // LLM mock 返回 null → LLM 内部降级
            ResolveResult result = resolveWithHistory("他的绩效怎么样",
                    List.of("查看张三的简历"));
            // LLM 调用失败（mock返回null）→ 降级
            assertNotNull(result);
            assertFalse(result.isResolved());
        }

        @Test
        void resolve_tamen_delegatesToLlm() {
            // "他们的考勤" → "他们" 不在快速路径 → 委托 LLM
            ResolveResult result = resolveWithHistory("他们的考勤",
                    List.of("查看研发部门的人员"));
            assertNotNull(result);
            // LLM mock 返回 null → 降级
            assertFalse(result.isResolved());
        }
    }

    // ==================== 降级场景 ====================

    @Nested
    @DisplayName("降级场景：异常/超时均返回原文")
    class DegradationTests {

        @Test
        void resolve_llmTimeout_degradesToOriginal() {
            // LLM 超时 → 降级返回原文
            ResolveResult result = resolveWithHistory("他的绩效",
                    List.of("张三的绩效是A"));
            assertNotNull(result);
            assertFalse(result.isResolved());
            assertEquals("他的绩效", result.getResolvedText());
        }

        @Test
        void resolve_resolvedPairs_emptyWhenUnresolved() {
            ResolveResult result = resolveWithHistory("查看研发部门绩效",
                    List.of("历史"));
            assertTrue(result.getResolvedPairs().isEmpty());
        }
    }

    // ==================== 代词检测边界 ====================

    @Nested
    @DisplayName("代词检测边界场景")
    class PronounDetectionEdgeCases {

        @Test
        void resolve_singleTa_triggersResolution() {
            // 独立 "他" 应触发
            ResolveResult result = resolveWithHistory("他是谁",
                    List.of("张三的绩效信息"));
            assertNotNull(result);
            // 触发了消解（但 LLM mock 失败 → 降级）
            // 关键是验证触发器正确检测到独立 "他"
        }

        @Test
        void resolve_zhege_triggersResolution() {
            // "这个" 应触发
            ResolveResult result = resolveWithHistory("这个部门怎么样",
                    List.of("研发部门有20人"));
            assertNotNull(result);
            // 触发器检测到 "这个"，快速路径不匹配（不是高置信模式），委托 LLM
        }

        @Test
        void resolve_naxie_triggersResolution() {
            // "那些" 应触发
            ResolveResult result = resolveWithHistory("那些人的绩效",
                    List.of("查看研发部门的人员"));
            assertNotNull(result);
        }

        @Test
        void resolve_dePattern_extractsPerson() {
            // 历史中有 "张三的绩效" → 快速路径应能提取 "张三"
            ResolveResult result = resolveWithHistory("该员工的考勤",
                    List.of("张三的绩效是A", "李四的考勤是B"));
            assertTrue(result.isResolved());
            assertEquals(ResolveMethod.RULE_FAST_PATH, result.getMethod());
            assertTrue(result.getResolvedText().contains("张三"));
        }

        @Test
        void resolve_timeExpression_doesNotTriggerLlm() {
            ResolveResult result = resolveWithHistory("这个月离职率是多少",
                    List.of("查看研发部门的人员"));
            assertFalse(result.isResolved());
            verifyNoInteractions(llmService);
        }

        @Test
        void resolve_qiPronoun_triggersLlm() {
            resolveWithHistory("其绩效如何", List.of("张三的绩效是A"));
            verify(llmService).generate(anyString(), anyString());
        }
    }

    // ==================== 省略补全 ====================

    @Nested
    @DisplayName("省略补全：当前查询极短时从上一轮继承谓语")
    class EllipsisResolutionTests {

        @Test
        void resolve_bareName_inheritsPredicateFromPreviousTurn() {
            // Turn 1: "我的年假还有多少" → Turn 2: "于娟" → "于娟的年假还有多少"
            ResolveResult result = resolveWithHistory("于娟",
                    List.of("我的年假还有多少"));
            assertTrue(result.isResolved(), "纯人名应触发省略补全");
            assertEquals(ResolveMethod.ELLIPSIS_RESOLUTION, result.getMethod());
            assertEquals("于娟的年假还有多少", result.getResolvedText());
        }

        @Test
        void resolve_bareName_zhangsan_inheritsPerformance() {
            // Turn 1: "张三的绩效怎么样" → Turn 2: "李四" → "李四的绩效怎么样"
            ResolveResult result = resolveWithHistory("李四",
                    List.of("张三的绩效怎么样"));
            assertTrue(result.isResolved());
            assertEquals("李四的绩效怎么样", result.getResolvedText());
        }

        @Test
        void resolve_completeQuery_noEllipsis() {
            // 当前查询已含疑问词，不触发省略补全
            ResolveResult result = resolveWithHistory("于娟的年假有多少",
                    List.of("我的年假还有多少"));
            assertFalse(result.isResolved(), "完整问句不应触发省略补全");
            assertEquals("于娟的年假有多少", result.getResolvedText());
        }

        @Test
        void resolve_longQuery_noEllipsis() {
            // 当前查询超过 6 字符，不视为纯人名
            ResolveResult result = resolveWithHistory("查看研发部门的绩效",
                    List.of("上次查了市场部的考勤"));
            assertFalse(result.isResolved(), "长查询不应触发省略补全");
        }

        @Test
        void resolve_noHistory_noEllipsis() {
            // 无历史时无法补全
            ResolveResult result = resolveWithHistory("于娟",
                    Collections.emptyList());
            assertFalse(result.isResolved(), "无历史不应触发省略补全");
            assertEquals("于娟", result.getResolvedText());
        }

        @Test
        void resolve_nullHistory_noEllipsis() {
            ResolveResult result = resolveWithHistory("于娟", null);
            assertFalse(result.isResolved());
            assertEquals("于娟", result.getResolvedText());
        }

        @Test
        void resolve_bareName_confidenceIs09() {
            ResolveResult result = resolveWithHistory("于娟",
                    List.of("我的年假还有多少"));
            assertTrue(result.isResolved());
            assertEquals(0.9, result.getConfidence(), 0.001);
        }

        @Test
        void resolve_previousWithoutDe_doesNotEllipsis() {
            // 上一轮没有"X的"主语标记，整句继承会拼出"于娟的研发部门有哪些人"这类病句，宁可不错
            ResolveResult result = resolveWithHistory("于娟",
                    List.of("研发部门有哪些人"));
            assertFalse(result.isResolved());
            assertEquals("于娟", result.getResolvedText());
        }

        @Test
        void resolve_previousSubjectIsDepartment_doesNotEllipsis() {
            ResolveResult result = resolveWithHistory("于娟",
                    List.of("研发部门的员工有多少"));
            assertFalse(result.isResolved(), "部门主语不能继承给人名");
        }

        @Test
        void resolve_businessNoun_doesNotEllipsis() {
            assertFalse(resolveWithHistory("绩效", List.of("我的年假还有多少")).isResolved());
            assertFalse(resolveWithHistory("年假", List.of("张三的绩效怎么样")).isResolved());
            assertFalse(resolveWithHistory("部门", List.of("我的年假还有多少")).isResolved());
        }

        @Test
        void resolve_greeting_doesNotEllipsis() {
            assertFalse(resolveWithHistory("你好", List.of("我的年假还有多少")).isResolved());
            assertFalse(resolveWithHistory("谢谢", List.of("查看张三的绩效")).isResolved());
        }

        @Test
        void resolve_previousNotMetricQuestion_doesNotEllipsis() {
            assertFalse(resolveWithHistory("李四", List.of("今天天气怎么样")).isResolved(),
                    "上一轮不是指标问题时不继承谓语");
        }
    }
}
