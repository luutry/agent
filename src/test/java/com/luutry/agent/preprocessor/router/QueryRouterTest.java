package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.cleaner.CleanPolicy;
import com.luutry.agent.preprocessor.cleaner.DefaultQueryCleaner;
import com.luutry.agent.preprocessor.cleaner.QueryInputDesensitizer;
import com.luutry.agent.preprocessor.model.IntentResult;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.preprocessor.model.QueryUnit;
import com.luutry.agent.preprocessor.resolver.CoreferenceResolver;
import com.luutry.agent.preprocessor.resolver.LlmCoreferenceResolver;
import com.luutry.agent.preprocessor.resolver.ResolvePolicy;
import com.luutry.agent.preprocessor.resolver.ResolveResult;
import com.luutry.agent.preprocessor.resolver.SimpleCoreferenceResolver;
import com.luutry.agent.llm.LlmService;
import com.luutry.agent.rag.vectorstore.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueryRouter (new pipeline: clean → resolve → decompose → classify → aggregate).
 */
class QueryRouterTest {

    private QueryRouter router;

    @BeforeEach
    void setUp() {
        ResolvePolicy resolvePolicy = new ResolvePolicy();
        LlmService mockLlmService = mock(LlmService.class);
        LlmCoreferenceResolver llmResolver = new LlmCoreferenceResolver(mockLlmService, resolvePolicy);

        IntentClassificationPolicy intentPolicy = new IntentClassificationPolicy();
        EmbeddingService mockEmbeddingService = mock(EmbeddingService.class);
        IntentWeightsLoader weightsLoader = new IntentWeightsLoader(new ArrayList<>(), List.of(0.0, 0.0, 0.0, 0.0));
        KeywordIntentClassifier keywordFallback = new KeywordIntentClassifier(
                intentPolicy.getFallbackToolKeywords(),
                intentPolicy.getFallbackRagKeywords(),
                intentPolicy.getFallbackChitchatKeywords()
        );

        router = new QueryRouter(
                new DefaultQueryCleaner(new CleanPolicy(), new QueryInputDesensitizer()),
                new SimpleCoreferenceResolver(resolvePolicy, llmResolver),
                new EmbeddingIntentClassifier(mockEmbeddingService, intentPolicy, weightsLoader, keywordFallback),
                new RuleBasedTaskDecomposer()
        );
    }

    @Test
    void route_toolQuery_classifiedCorrectly() {
        ProcessedQuery result = router.route("查看研发部门员工绩效");
        assertEquals(QueryIntent.TOOL, result.getIntent());
        assertNotNull(result.getFinalQuery());
        assertEquals(1, result.getUnits().size());
        assertEquals(QueryIntent.TOOL, result.getUnits().get(0).getIntent());
    }

    @Test
    void route_ragQuery_classifiedCorrectly() {
        ProcessedQuery result = router.route("离职制度是怎么规定的");
        assertEquals(QueryIntent.RAG, result.getIntent());
    }

    @Test
    void route_emptyQuery_returnsChitchat() {
        ProcessedQuery result = router.route("");
        assertEquals(QueryIntent.CHITCHAT, result.getIntent());
        assertEquals(0.0, result.getConfidence());
    }

    @Test
    void route_twoIndependentRequests_splitsIntoTwoUnits() {
        // 两个独立谓语（查看 / 看看），应拆分
        ProcessedQuery result = router.route("查看员工绩效，同时看看考勤情况");
        assertTrue(result.isComposite());
        assertEquals(2, result.getUnits().size());
    }

    @Test
    void route_multiQuestion_splitsIntoTwoUnits() {
        ProcessedQuery result = router.route("团队有多少人？离职率是多少？");
        assertTrue(result.isComposite());
        assertEquals(2, result.getUnits().size());
    }

    @Test
    void route_explicitMarker_splitsIntoTwoUnits() {
        ProcessedQuery result = router.route("我年假剩几天，另外社保基数多少");
        assertTrue(result.isComposite());
        assertEquals(2, result.getUnits().size());
        assertEquals("我年假剩几天", result.getUnits().get(0).getQuery());
        assertEquals("社保基数多少", result.getUnits().get(1).getQuery());
    }

    @Test
    void route_remainingWord_doesNotSplit() {
        // "还有"表剩余，不是并列标记
        ProcessedQuery result = router.route("我年假还有几天");
        assertFalse(result.isComposite());
        assertEquals(1, result.getUnits().size());
    }

    @Test
    void route_sharedPredicate_doesNotSplit() {
        // 并列宾语共享谓语"的区别"
        ProcessedQuery result = router.route("年假以及病假的区别");
        assertFalse(result.isComposite());
        assertEquals(1, result.getUnits().size());
    }

    @Test
    void route_greetingPlusSingleIntent_doesNotSplit() {
        ProcessedQuery result = router.route("你好，我想问下年假");
        assertFalse(result.isComposite());
        assertEquals(1, result.getUnits().size());
    }

    @Test
    void route_sharedVerbParallelObjects_doesNotSplit() {
        // 共享谓语"查一下"，并列宾语不拆
        ProcessedQuery result = router.route("查一下张三的绩效和考勤");
        assertFalse(result.isComposite());
        assertEquals(1, result.getUnits().size());
    }

    @Test
    void route_enumerationWithFenbie_splitsIntoTwoUnits() {
        // "分别"把共享谓语分发给两个宾语，各自补全成完整问题
        ProcessedQuery result = router.route("年假、病假分别几天");
        assertTrue(result.isComposite());
        assertEquals(2, result.getUnits().size());
        assertEquals("年假几天", result.getUnits().get(0).getQuery());
        assertEquals("病假几天", result.getUnits().get(1).getQuery());
    }

    @Test
    void route_withHistory_coreferenceResolved() {
        List<String> history = List.of("查看张三的简历信息");
        ProcessedQuery result = router.route("他的绩效怎么样", history);
        assertNotNull(result.getResolvedQuery());
    }

    @Test
    void route_originalQuery_preserved() {
        ProcessedQuery result = router.route("查看员工绩效");
        assertEquals("查看员工绩效", result.getOriginalQuery());
    }

    @Test
    void route_cleanedQuery_processed() {
        ProcessedQuery result = router.route("  查看  \t 员工绩效  ");
        assertEquals("查看 员工绩效", result.getCleanedQuery());
    }

    @Test
    void route_singleQuery_notComposite() {
        ProcessedQuery result = router.route("查看员工绩效");
        assertFalse(result.isComposite());
        assertEquals(1, result.getUnits().size());
    }

    @Test
    void route_confidence_inRange() {
        ProcessedQuery result = router.route("查看员工绩效");
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
    }

    @Test
    void route_unitQueryIsOriginalText_notExpanded() {
        // 关键验证：unit 的 query 是消解后原文，不带同义词扩展
        ProcessedQuery result = router.route("离职流程怎么走");
        assertEquals(1, result.getUnits().size());
        String unitQuery = result.getUnits().get(0).getQuery();
        assertFalse(unitQuery.contains("流失"), "unit query 不应包含扩展词");
        assertFalse(unitQuery.contains("辞退"), "unit query 不应包含扩展词");
    }

    @Test
    void route_entitiesEmpty_noKeywordExtraction() {
        // 关键词提取已移出管道，entities 应为空
        ProcessedQuery result = router.route("查看研发部门员工绩效");
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    void route_finalQuery_equalsResolved() {
        // rewrite 已移出管道，finalQuery 应等于 resolved
        ProcessedQuery result = router.route("查看员工绩效");
        assertEquals(result.getResolvedQuery(), result.getFinalQuery());
    }

    @Test
    void route_historyWithPii_maskedBeforeResolve() {
        CoreferenceResolver resolver = mock(CoreferenceResolver.class);
        when(resolver.resolve(anyString(), anyList())).thenReturn(ResolveResult.unresolved("他呢"));

        IntentClassifier classifier = mock(IntentClassifier.class);
        when(classifier.classify(anyString()))
                .thenReturn(new IntentResult(QueryIntent.TOOL, 0.9, Collections.emptyMap()));

        QueryRouter maskedRouter = new QueryRouter(
                new DefaultQueryCleaner(new CleanPolicy(), new QueryInputDesensitizer()),
                resolver,
                classifier,
                new RuleBasedTaskDecomposer());

        maskedRouter.route("他呢", List.of("联系 138 1234 5678 的张三"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(resolver).resolve(eq("他呢"), captor.capture());
        assertEquals(List.of("联系 138****5678 的张三"), captor.getValue(),
                "history 必须先脱敏再交给消解器");
    }
}
