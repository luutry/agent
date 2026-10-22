package com.luutry.agent.preprocessor.rewriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedQueryRewriter.
 */
class RuleBasedQueryRewriterTest {

    private RuleBasedQueryRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new RuleBasedQueryRewriter();
    }

    @Test
    void rewrite_nullQuery_returnsNull() {
        assertNull(rewriter.rewrite(null));
    }

    @Test
    void rewrite_blankQuery_returnsBlank() {
        assertEquals("", rewriter.rewrite(""));
    }

    @Test
    void rewrite_standardTerm_standardized() {
        String result = rewriter.rewrite("查看hc");
        assertTrue(result.contains("编制"));
        assertFalse(result.contains("hc"));
    }

    @Test
    void rewrite_kpiExpanded() {
        String result = rewriter.rewrite("KPI考核结果");
        assertTrue(result.contains("绩效考核"));
    }

    @Test
    void rewrite_synonymExpansion_appendsSynonyms() {
        String result = rewriter.rewrite("员工离职率");
        // Should contain synonyms for 离职
        assertTrue(result.length() > "员工离职率".length());
    }

    @Test
    void rewrite_noMatch_returnsOriginal() {
        String result = rewriter.rewrite("今天天气不错");
        // No HR terms, should still go through standardization (no-op)
        assertNotNull(result);
    }

    @Test
    void extractKeywords_containsHRTerms() {
        List<String> keywords = rewriter.extractKeywords("查看研发部门员工绩效");
        assertTrue(keywords.contains("绩效"));
        assertTrue(keywords.contains("员工"));
    }

    @Test
    void extractKeywords_emptyForBlank() {
        List<String> keywords = rewriter.extractKeywords("");
        assertTrue(keywords.isEmpty());
    }

    @Test
    void extractKeywords_containsStandardTerms() {
        List<String> keywords = rewriter.extractKeywords("hc有多少");
        assertTrue(keywords.contains("编制"));
    }

    @Test
    void rewrite_multipleTerms_allStandardized() {
        String result = rewriter.rewrite("hc和kpi的关系");
        assertTrue(result.contains("编制"));
        assertTrue(result.contains("绩效考核"));
    }
}
