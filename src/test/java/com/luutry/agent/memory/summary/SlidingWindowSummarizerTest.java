package com.luutry.agent.memory.summary;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.model.ConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlidingWindowSummarizer.
 * Covers: summarization, merge, window splitting, token control.
 */
@DisplayName("SlidingWindowSummarizer Tests")
class SlidingWindowSummarizerTest {

    private SlidingWindowSummarizer summarizer;

    @BeforeEach
    void setUp() {
        summarizer = new SlidingWindowSummarizer();
    }

    @Test
    @DisplayName("summarize - generates summary from turns")
    void testSummarize() {
        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().turnNumber(1)
                        .userMessage("What is the team size?")
                        .assistantMessage("The team has 10 members.")
                        .build(),
                ConversationTurn.builder().turnNumber(2)
                        .userMessage("Show turnover rate")
                        .assistantMessage("Turnover rate is 5%.")
                        .build()
        );

        String summary = summarizer.summarize(turns);
        assertNotNull(summary);
        assertTrue(summary.startsWith("[Summary]"));
        assertTrue(summary.contains("team size") || summary.contains("team"));
        assertTrue(summary.contains("turnover"));
    }

    @Test
    @DisplayName("summarize - empty list returns empty string")
    void testSummarizeEmpty() {
        assertEquals("", summarizer.summarize(Collections.emptyList()));
        assertEquals("", summarizer.summarize(null));
    }

    @Test
    @DisplayName("summarize - truncates to max length")
    void testSummarizeMaxLength() {
        // Create a very long turn
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longText.append("This is a very long sentence number ").append(i).append(". ");
        }

        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().turnNumber(1)
                        .userMessage(longText.toString())
                        .assistantMessage(longText.toString())
                        .build()
        );

        String summary = summarizer.summarize(turns);
        assertTrue(summary.length() <= AgentConstants.MAX_SUMMARY_LENGTH);
    }

    @Test
    @DisplayName("needsSummarization - returns true when exceeding window")
    void testNeedsSummarization() {
        assertFalse(summarizer.needsSummarization(0));
        assertFalse(summarizer.needsSummarization(AgentConstants.SUMMARY_WINDOW_SIZE));
        assertTrue(summarizer.needsSummarization(AgentConstants.SUMMARY_WINDOW_SIZE + 1));
        assertTrue(summarizer.needsSummarization(100));
    }

    @Test
    @DisplayName("splitForSummarization - splits correctly when exceeding window")
    void testSplitForSummarization() {
        List<ConversationTurn> turns = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            turns.add(ConversationTurn.builder()
                    .turnNumber(i)
                    .userMessage("Q" + i)
                    .assistantMessage("A" + i)
                    .build());
        }

        ConversationSummarizer.SplitResult result = summarizer.splitForSummarization(turns);

        // Window size is 6, so 10-6=4 turns should be summarized
        assertEquals(4, result.toSummarize().size());
        assertEquals(6, result.toKeep().size());
        assertEquals(1, result.toSummarize().get(0).getTurnNumber());
        assertEquals(5, result.toKeep().get(0).getTurnNumber());
    }

    @Test
    @DisplayName("splitForSummarization - no split when within window")
    void testSplitForSummarizationWithinWindow() {
        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().turnNumber(1).userMessage("Q").assistantMessage("A").build(),
                ConversationTurn.builder().turnNumber(2).userMessage("Q").assistantMessage("A").build()
        );

        ConversationSummarizer.SplitResult result = summarizer.splitForSummarization(turns);
        assertTrue(result.toSummarize().isEmpty());
        assertEquals(2, result.toKeep().size());
    }

    @Test
    @DisplayName("mergeAndSummarize - merges with existing summary")
    void testMergeAndSummarize() {
        String existing = "[Summary] Q:Team size A:10 members";
        List<ConversationTurn> newTurns = List.of(
                ConversationTurn.builder().turnNumber(3)
                        .userMessage("New question about performance")
                        .assistantMessage("Performance data here")
                        .build()
        );

        String merged = summarizer.mergeAndSummarize(existing, newTurns);
        assertNotNull(merged);
        assertTrue(merged.startsWith("[Summary]"));
        assertTrue(merged.length() <= AgentConstants.MAX_SUMMARY_LENGTH);
    }

    @Test
    @DisplayName("mergeAndSummarize - null existing returns new summary")
    void testMergeAndSummarizeNullExisting() {
        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().turnNumber(1)
                        .userMessage("Q").assistantMessage("A").build()
        );

        String result = summarizer.mergeAndSummarize(null, turns);
        assertTrue(result.startsWith("[Summary]"));
    }

    @Test
    @DisplayName("mergeAndSummarize - empty new turns returns existing")
    void testMergeAndSummarizeEmptyNew() {
        String existing = "[Summary] existing content";
        String result = summarizer.mergeAndSummarize(existing, Collections.emptyList());
        assertEquals(existing, result);
    }

    @Test
    @DisplayName("summarize - includes tool call count")
    void testSummarizeWithTools() {
        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().turnNumber(1)
                        .userMessage("Search employees")
                        .assistantMessage("Found 5 results")
                        .toolResults(List.of("employee_search returned 5"))
                        .build()
        );

        String summary = summarizer.summarize(turns);
        assertTrue(summary.contains("Tools:1 called"));
    }
}
