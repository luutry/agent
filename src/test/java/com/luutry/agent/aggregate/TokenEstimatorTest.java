package com.luutry.agent.aggregate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenEstimator utility.
 */
class TokenEstimatorTest {

    @Test
    void estimate_nullInput_returnsZero() {
        assertEquals(0, TokenEstimator.estimate(null));
    }

    @Test
    void estimate_blankInput_returnsZero() {
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate("   "));
    }

    @Test
    void estimate_pureEnglish_estimatesByCharDiv4() {
        // "hello world" = 10 non-whitespace chars / 4 = 2.5 -> ceil = 3
        int tokens = TokenEstimator.estimate("hello world");
        assertTrue(tokens >= 2 && tokens <= 4, "Expected ~3 tokens, got " + tokens);
    }

    @Test
    void estimate_pureChinese_appliesCorrectionRatio() {
        // "你好世界" = 4 Chinese chars * 1.5 = 6 tokens
        int tokens = TokenEstimator.estimate("你好世界");
        assertEquals(6, tokens);
    }

    @Test
    void estimate_mixedText_combinesBothRatios() {
        // "hello你好" = 5 English chars / 4 + 2 Chinese * 1.5 = 1.25 + 3.0 = 4.25 -> ceil = 5
        int tokens = TokenEstimator.estimate("hello你好");
        assertTrue(tokens >= 4 && tokens <= 6, "Expected ~5 tokens, got " + tokens);
    }

    @Test
    void estimate_singleChar_returnsMinimumOne() {
        assertEquals(1, TokenEstimator.estimate("a"));
        // Chinese char: 1 * 1.5 = 1.5 -> ceil = 2
        assertEquals(2, TokenEstimator.estimate("中"));
    }

    @Test
    void wouldExceedBudget_withinBudget_returnsFalse() {
        assertFalse(TokenEstimator.wouldExceedBudget(100, "short", 4096));
    }

    @Test
    void wouldExceedBudget_exceedsBudget_returnsTrue() {
        assertTrue(TokenEstimator.wouldExceedBudget(4090, "a very long text that will exceed the budget limit", 4096));
    }

    @Test
    void truncateToFit_shortText_returnsOriginal() {
        String text = "short text";
        assertEquals(text, TokenEstimator.truncateToFit(text, 100));
    }

    @Test
    void truncateToFit_longText_truncates() {
        String text = "This is a very long text that should be truncated to fit within the budget";
        String truncated = TokenEstimator.truncateToFit(text, 5);
        assertNotNull(truncated);
        assertTrue(truncated.length() < text.length());
        assertTrue(TokenEstimator.estimate(truncated) <= 6, "Truncated should fit within ~5 tokens");
    }

    @Test
    void truncateToFit_nullInput_returnsNull() {
        assertNull(TokenEstimator.truncateToFit(null, 10));
    }

    @Test
    void truncateToFit_blankInput_returnsBlank() {
        assertEquals("", TokenEstimator.truncateToFit("", 10));
    }
}
