package com.luutry.agent.memory;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.decay.MemoryDecayScheduler;
import com.luutry.agent.memory.longterm.InMemoryLongTermStore;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.shortterm.InMemoryShortTermStore;
import com.luutry.agent.memory.summary.SlidingWindowSummarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryManager.
 * Covers: three-layer coordination, write dispatch, retrieval merging, isolation.
 */
@DisplayName("MemoryManager Tests")
class MemoryManagerTest {

    private MemoryManager memoryManager;
    private InMemoryShortTermStore shortTermStore;
    private InMemoryLongTermStore longTermStore;

    @BeforeEach
    void setUp() {
        shortTermStore = new InMemoryShortTermStore();
        longTermStore = new InMemoryLongTermStore();
        SlidingWindowSummarizer summarizer = new SlidingWindowSummarizer();
        MemoryDecayScheduler decayScheduler = new MemoryDecayScheduler(longTermStore);
        memoryManager = new MemoryManager(shortTermStore, longTermStore, summarizer, decayScheduler);
    }

    @Test
    @DisplayName("addToSession - adds turn to short-term memory")
    void testAddToSession() {
        ConversationTurn turn = ConversationTurn.builder()
                .turnNumber(1)
                .userMessage("Show team metrics")
                .assistantMessage("Team has 10 members")
                .build();

        memoryManager.addToSession("session-1", "user-1", turn);

        assertEquals(1, memoryManager.getSessionTurnCount("session-1"));
        List<ConversationTurn> turns = shortTermStore.getAllTurns("session-1");
        assertEquals(1, turns.size());
        assertEquals("Show team metrics", turns.get(0).getUserMessage());
    }

    @Test
    @DisplayName("addToSession - triggers summarization when exceeding window")
    void testAddToSessionTriggersSummarization() {
        // Add more turns than the window size
        for (int i = 1; i <= AgentConstants.SUMMARY_WINDOW_SIZE + 2; i++) {
            ConversationTurn turn = ConversationTurn.builder()
                    .turnNumber(i)
                    .userMessage("Question " + i)
                    .assistantMessage("Answer " + i)
                    .build();
            memoryManager.addToSession("session-2", "user-1", turn);
        }

        // Summary should have been generated
        String summary = memoryManager.getSessionSummary("session-2");
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        assertTrue(summary.startsWith("[Summary]"));
    }

    @Test
    @DisplayName("saveLongTerm - saves to long-term store")
    void testSaveLongTerm() {
        memoryManager.saveLongTerm("user-1", "Prefers concise answers", "preference");
        memoryManager.saveLongTerm("user-1", "Manages tech department", "organization");

        List<MemoryEntry> memories = memoryManager.getUserLongTermMemories("user-1");
        assertEquals(2, memories.size());
    }

    @Test
    @DisplayName("retrieveRelevant - searches long-term memory")
    void testRetrieveRelevant() {
        memoryManager.saveLongTerm("user-1", "Team turnover rate is increasing", "insight");
        memoryManager.saveLongTerm("user-1", "Employee satisfaction survey results", "report");
        memoryManager.saveLongTerm("user-1", "Department headcount metrics", "metrics");

        List<MemoryEntry> results = memoryManager.retrieveRelevant("user-1", "turnover rate", 5);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("turnover"));
    }

    @Test
    @DisplayName("getSessionContext - returns context with summary and turns")
    void testGetSessionContext() {
        // Add some turns
        for (int i = 1; i <= 3; i++) {
            memoryManager.addToSession("session-3", "user-1",
                    ConversationTurn.builder()
                            .turnNumber(i)
                            .userMessage("Q" + i)
                            .assistantMessage("A" + i)
                            .build());
        }

        MemoryManager.SessionContext ctx = memoryManager.getSessionContext("session-3");
        assertNotNull(ctx);
        assertEquals(3, ctx.recentTurns().size());
        assertFalse(ctx.isEmpty());
    }

    @Test
    @DisplayName("getSessionContext - empty for unknown session")
    void testGetSessionContextEmpty() {
        MemoryManager.SessionContext ctx = memoryManager.getSessionContext("unknown");
        assertNotNull(ctx);
        assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("clearSession - removes both short-term and summary")
    void testClearSession() {
        memoryManager.addToSession("session-4", "user-1",
                ConversationTurn.builder()
                        .turnNumber(1)
                        .userMessage("Q")
                        .assistantMessage("A")
                        .build());

        memoryManager.clearSession("session-4");

        assertEquals(0, memoryManager.getSessionTurnCount("session-4"));
        assertEquals("", memoryManager.getSessionSummary("session-4"));
    }

    @Test
    @DisplayName("User isolation - different users have separate memories")
    void testUserIsolation() {
        memoryManager.saveLongTerm("user-A", "User A preference for charts", "preference");
        memoryManager.saveLongTerm("user-B", "User B preference for tables", "preference");

        List<MemoryEntry> userAMemories = memoryManager.getUserLongTermMemories("user-A");
        List<MemoryEntry> userBMemories = memoryManager.getUserLongTermMemories("user-B");

        assertEquals(1, userAMemories.size());
        assertEquals(1, userBMemories.size());
        assertTrue(userAMemories.get(0).getContent().contains("charts"));
        assertTrue(userBMemories.get(0).getContent().contains("tables"));
    }

    @Test
    @DisplayName("runDecayCycle - delegates to decay scheduler")
    void testRunDecayCycle() {
        memoryManager.saveLongTerm("user-1", "Fresh memory", "tag");
        int forgotten = memoryManager.runDecayCycle("user-1");
        // Fresh memory should not be forgotten
        assertEquals(0, forgotten);
        assertEquals(1, memoryManager.getUserLongTermMemories("user-1").size());
    }

    @Test
    @DisplayName("SessionContext - estimateTokens works correctly")
    void testSessionContextEstimateTokens() {
        memoryManager.addToSession("session-5", "user-1",
                ConversationTurn.builder()
                        .turnNumber(1)
                        .userMessage("Hello") // 5 chars
                        .assistantMessage("Hi there") // 8 chars
                        .build());

        MemoryManager.SessionContext ctx = memoryManager.getSessionContext("session-5");
        assertTrue(ctx.estimateTokens() > 0);
    }

    @Test
    @DisplayName("addToSession - null params throw NPE")
    void testAddToSessionNullParams() {
        assertThrows(NullPointerException.class, () ->
                memoryManager.addToSession(null, "user", ConversationTurn.builder()
                        .turnNumber(1).userMessage("Q").assistantMessage("A").build()));

        assertThrows(NullPointerException.class, () ->
                memoryManager.addToSession("session", null, ConversationTurn.builder()
                        .turnNumber(1).userMessage("Q").assistantMessage("A").build()));

        assertThrows(NullPointerException.class, () ->
                memoryManager.addToSession("session", "user", null));
    }

    @Test
    @DisplayName("retrieveRelevant - empty results for no match")
    void testRetrieveRelevantNoMatch() {
        memoryManager.saveLongTerm("user-1", "Something about performance reviews", "tag");

        List<MemoryEntry> results = memoryManager.retrieveRelevant("user-1", "xyzabc nomatch", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("getSessionContext - with summarization splits correctly")
    void testGetSessionContextWithSummarization() {
        // Add more turns than window size
        for (int i = 1; i <= AgentConstants.SUMMARY_WINDOW_SIZE + 3; i++) {
            memoryManager.addToSession("session-6", "user-1",
                    ConversationTurn.builder()
                            .turnNumber(i)
                            .userMessage("Question " + i)
                            .assistantMessage("Answer " + i)
                            .build());
        }

        MemoryManager.SessionContext ctx = memoryManager.getSessionContext("session-6");
        assertNotNull(ctx);
        // Summary should exist
        assertFalse(ctx.summary().isBlank());
        // Recent turns should be limited to window size
        assertTrue(ctx.recentTurns().size() <= AgentConstants.SUMMARY_WINDOW_SIZE);
    }
}
