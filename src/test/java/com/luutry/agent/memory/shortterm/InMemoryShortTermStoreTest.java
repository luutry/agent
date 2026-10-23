package com.luutry.agent.memory.shortterm;

import com.luutry.agent.memory.model.ConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryShortTermStore.
 * Covers: CRUD, TTL simulation, session isolation, boundary conditions.
 */
@DisplayName("InMemoryShortTermStore Tests")
class InMemoryShortTermStoreTest {

    private InMemoryShortTermStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryShortTermStore();
    }

    @Test
    @DisplayName("addTurn and getAllTurns - basic session operations")
    void testAddAndGetTurns() {
        String sessionId = "session-1";
        String userId = "user-1";

        ConversationTurn turn1 = ConversationTurn.builder()
                .turnNumber(1)
                .userMessage("Show team metrics")
                .assistantMessage("Here are the metrics...")
                .build();

        ConversationTurn turn2 = ConversationTurn.builder()
                .turnNumber(2)
                .userMessage("What about turnover rate?")
                .assistantMessage("Turnover rate is 5%...")
                .build();

        store.addTurn(sessionId, userId, turn1);
        store.addTurn(sessionId, userId, turn2);

        List<ConversationTurn> turns = store.getAllTurns(sessionId);
        assertEquals(2, turns.size());
        assertEquals("Show team metrics", turns.get(0).getUserMessage());
        assertEquals("What about turnover rate?", turns.get(1).getUserMessage());
    }

    @Test
    @DisplayName("getRecentTurns - returns last N turns")
    void testGetRecentTurns() {
        String sessionId = "session-2";
        String userId = "user-1";

        for (int i = 1; i <= 10; i++) {
            store.addTurn(sessionId, userId, ConversationTurn.builder()
                    .turnNumber(i)
                    .userMessage("Question " + i)
                    .assistantMessage("Answer " + i)
                    .build());
        }

        List<ConversationTurn> recent = store.getRecentTurns(sessionId, 3);
        assertEquals(3, recent.size());
        assertEquals("Question 8", recent.get(0).getUserMessage());
        assertEquals("Question 10", recent.get(2).getUserMessage());
    }

    @Test
    @DisplayName("getRecentTurns - limit exceeds total returns all")
    void testGetRecentTurnsLimitExceedsTotal() {
        String sessionId = "session-3";
        String userId = "user-1";

        store.addTurn(sessionId, userId, ConversationTurn.builder()
                .turnNumber(1).userMessage("Q").assistantMessage("A").build());

        List<ConversationTurn> recent = store.getRecentTurns(sessionId, 10);
        assertEquals(1, recent.size());
    }

    @Test
    @DisplayName("getTurnCount - correct count")
    void testGetTurnCount() {
        String sessionId = "session-4";
        String userId = "user-1";

        assertEquals(0, store.getTurnCount(sessionId));

        store.addTurn(sessionId, userId, ConversationTurn.builder()
                .turnNumber(1).userMessage("Q1").assistantMessage("A1").build());
        store.addTurn(sessionId, userId, ConversationTurn.builder()
                .turnNumber(2).userMessage("Q2").assistantMessage("A2").build());

        assertEquals(2, store.getTurnCount(sessionId));
    }

    @Test
    @DisplayName("clearSession - removes all data")
    void testClearSession() {
        String sessionId = "session-5";
        String userId = "user-1";

        store.addTurn(sessionId, userId, ConversationTurn.builder()
                .turnNumber(1).userMessage("Q").assistantMessage("A").build());

        assertTrue(store.sessionExists(sessionId));
        store.clearSession(sessionId);
        assertFalse(store.sessionExists(sessionId));
        assertEquals(0, store.getTurnCount(sessionId));
    }

    @Test
    @DisplayName("sessionExists - returns false for unknown session")
    void testSessionExistsFalse() {
        assertFalse(store.sessionExists("nonexistent-session"));
    }

    @Test
    @DisplayName("getSessionUserId - returns correct user")
    void testGetSessionUserId() {
        String sessionId = "session-6";
        String userId = "user-abc";

        store.addTurn(sessionId, userId, ConversationTurn.builder()
                .turnNumber(1).userMessage("Q").assistantMessage("A").build());

        Optional<String> result = store.getSessionUserId(sessionId);
        assertTrue(result.isPresent());
        assertEquals(userId, result.get());
    }

    @Test
    @DisplayName("getSessionUserId - empty for unknown session")
    void testGetSessionUserIdEmpty() {
        Optional<String> result = store.getSessionUserId("unknown");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("getAllTurns - empty list for unknown session")
    void testGetAllTurnsEmpty() {
        List<ConversationTurn> turns = store.getAllTurns("unknown-session");
        assertNotNull(turns);
        assertTrue(turns.isEmpty());
    }

    @Test
    @DisplayName("addTurn - null sessionId throws NPE")
    void testAddTurnNullSessionId() {
        assertThrows(NullPointerException.class, () ->
                store.addTurn(null, "user", ConversationTurn.builder()
                        .turnNumber(1).userMessage("Q").assistantMessage("A").build()));
    }

    @Test
    @DisplayName("addTurn - null userId throws NPE")
    void testAddTurnNullUserId() {
        assertThrows(NullPointerException.class, () ->
                store.addTurn("session", null, ConversationTurn.builder()
                        .turnNumber(1).userMessage("Q").assistantMessage("A").build()));
    }

    @Test
    @DisplayName("addTurn - null turn throws NPE")
    void testAddTurnNullTurn() {
        assertThrows(NullPointerException.class, () ->
                store.addTurn("session", "user", null));
    }

    @Test
    @DisplayName("Session isolation - different sessions don't interfere")
    void testSessionIsolation() {
        store.addTurn("s1", "u1", ConversationTurn.builder()
                .turnNumber(1).userMessage("Q1").assistantMessage("A1").build());
        store.addTurn("s2", "u2", ConversationTurn.builder()
                .turnNumber(1).userMessage("Q2").assistantMessage("A2").build());

        assertEquals(1, store.getTurnCount("s1"));
        assertEquals(1, store.getTurnCount("s2"));
        assertEquals(Optional.of("u1"), store.getSessionUserId("s1"));
        assertEquals(Optional.of("u2"), store.getSessionUserId("s2"));
    }

    @Test
    @DisplayName("addTurn - returns sequential turn numbers")
    void testAddTurnReturnsSequence() {
        assertEquals(1, store.addTurn("s1", "u1", ConversationTurn.builder()
                .userMessage("Q1").assistantMessage("A1").build()));
        assertEquals(2, store.addTurn("s1", "u1", ConversationTurn.builder()
                .userMessage("Q2").assistantMessage("A2").build()));
    }

    @Test
    @DisplayName("summary - save, read and clear alongside turns")
    void testSummaryLifecycle() {
        String sessionId = "session-7";
        store.addTurn(sessionId, "user-1", ConversationTurn.builder()
                .userMessage("Q").assistantMessage("A").build());

        store.saveSummary(sessionId, "[Summary] Q | A");
        assertEquals("[Summary] Q | A", store.getSummary(sessionId));

        store.clearSession(sessionId);
        assertEquals("", store.getSummary(sessionId));
    }

    @Test
    @DisplayName("getSummary - empty for unknown session")
    void testSummaryEmpty() {
        assertEquals("", store.getSummary("unknown"));
    }
}
