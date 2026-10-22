package com.luutry.agent.memory.longterm;

import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryLongTermStore.
 * Covers: CRUD, semantic search simulation, TTL, decay cleanup, isolation.
 */
@DisplayName("InMemoryLongTermStore Tests")
class InMemoryLongTermStoreTest {

    private InMemoryLongTermStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryLongTermStore();
    }

    @Test
    @DisplayName("save and getById - basic CRUD")
    void testSaveAndGet() {
        MemoryEntry entry = createEntry("user-1", "Prefers team overview format", "preference");
        store.save(entry);

        MemoryEntry retrieved = store.getById(entry.getId());
        assertNotNull(retrieved);
        assertEquals(entry.getId(), retrieved.getId());
        assertEquals("Prefers team overview format", retrieved.getContent());
    }

    @Test
    @DisplayName("getByUserId - returns user's memories")
    void testGetByUserId() {
        store.save(createEntry("user-1", "Memory A", "tag1"));
        store.save(createEntry("user-1", "Memory B", "tag2"));
        store.save(createEntry("user-2", "Memory C", "tag1"));

        List<MemoryEntry> user1Memories = store.getByUserId("user-1");
        assertEquals(2, user1Memories.size());

        List<MemoryEntry> user2Memories = store.getByUserId("user-2");
        assertEquals(1, user2Memories.size());
    }

    @Test
    @DisplayName("getByTag - filters by tag")
    void testGetByTag() {
        store.save(createEntry("user-1", "Prefers charts", "preference"));
        store.save(createEntry("user-1", "Manages tech dept", "organization"));
        store.save(createEntry("user-1", "Likes concise answers", "preference"));

        List<MemoryEntry> prefs = store.getByTag("user-1", "preference");
        assertEquals(2, prefs.size());
    }

    @Test
    @DisplayName("search - returns relevant results by keyword overlap")
    void testSearch() {
        store.save(createEntry("user-1", "Team turnover rate analysis report", "report"));
        store.save(createEntry("user-1", "Employee performance review summary", "review"));
        store.save(createEntry("user-1", "Department headcount metrics", "metrics"));

        List<MemoryEntry> results = store.search("user-1", "turnover rate", 5);
        assertFalse(results.isEmpty());
        // First result should be the one about turnover
        assertTrue(results.get(0).getContent().contains("turnover") ||
                results.get(0).getContent().contains("rate"));
    }

    @Test
    @DisplayName("search - empty query returns empty")
    void testSearchEmptyQuery() {
        store.save(createEntry("user-1", "Some content", "tag"));
        assertTrue(store.search("user-1", "", 5).isEmpty());
        assertTrue(store.search("user-1", "  ", 5).isEmpty());
    }

    @Test
    @DisplayName("search - respects topK limit")
    void testSearchTopK() {
        for (int i = 0; i < 10; i++) {
            store.save(createEntry("user-1", "Data analysis report " + i, "report"));
        }

        List<MemoryEntry> results = store.search("user-1", "data analysis report", 3);
        assertTrue(results.size() <= 3);
    }

    @Test
    @DisplayName("delete - removes entry")
    void testDelete() {
        MemoryEntry entry = createEntry("user-1", "To be deleted", "temp");
        store.save(entry);

        assertTrue(store.delete(entry.getId()));
        assertNull(store.getById(entry.getId()));
    }

    @Test
    @DisplayName("delete - returns false for non-existent")
    void testDeleteNonExistent() {
        assertFalse(store.delete("non-existent-id"));
    }

    @Test
    @DisplayName("deleteByUserId - removes all user memories")
    void testDeleteByUserId() {
        store.save(createEntry("user-1", "M1", "tag"));
        store.save(createEntry("user-1", "M2", "tag"));
        store.save(createEntry("user-2", "M3", "tag"));

        int deleted = store.deleteByUserId("user-1");
        assertEquals(2, deleted);
        assertEquals(0, store.getByUserId("user-1").size());
        assertEquals(1, store.getByUserId("user-2").size());
    }

    @Test
    @DisplayName("removeExpired - cleans up TTL entries")
    void testRemoveExpired() {
        MemoryEntry expired = createEntry("user-1", "Expired memory", "tag");
        expired.setExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));
        store.save(expired);

        MemoryEntry valid = createEntry("user-1", "Valid memory", "tag");
        valid.setExpireAt(Instant.now().plus(1, ChronoUnit.HOURS));
        store.save(valid);

        int removed = store.removeExpired();
        assertEquals(1, removed);
        assertNull(store.getById(expired.getId()));
        assertNotNull(store.getById(valid.getId()));
    }

    @Test
    @DisplayName("forgetByDecayScore - removes low-score entries")
    void testForgetByDecayScore() {
        MemoryEntry lowScore = createEntry("user-1", "Low score memory", "tag");
        lowScore.setDecayScore(0.05);
        store.save(lowScore);

        MemoryEntry highScore = createEntry("user-1", "High score memory", "tag");
        highScore.setDecayScore(0.8);
        store.save(highScore);

        int forgotten = store.forgetByDecayScore(0.1);
        assertEquals(1, forgotten);
        assertNull(store.getById(lowScore.getId()));
        assertNotNull(store.getById(highScore.getId()));
    }

    @Test
    @DisplayName("count - returns correct count excluding expired")
    void testCount() {
        store.save(createEntry("user-1", "M1", "tag"));
        store.save(createEntry("user-1", "M2", "tag"));

        MemoryEntry expired = createEntry("user-1", "M3", "tag");
        expired.setExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));
        store.save(expired);

        assertEquals(2, store.count("user-1"));
    }

    @Test
    @DisplayName("search - user isolation, cannot see other user's memories")
    void testSearchIsolation() {
        store.save(createEntry("user-1", "User 1 private memory about team metrics", "tag"));
        store.save(createEntry("user-2", "User 2 private memory about team metrics", "tag"));

        List<MemoryEntry> user1Results = store.search("user-1", "team metrics", 5);
        assertEquals(1, user1Results.size());
        assertTrue(user1Results.get(0).getContent().contains("User 1"));
    }

    @Test
    @DisplayName("getById - increments access count")
    void testGetByIdIncrementsAccess() {
        MemoryEntry entry = createEntry("user-1", "Content", "tag");
        store.save(entry);
        assertEquals(0, entry.getAccessCount());

        store.getById(entry.getId());
        assertEquals(1, entry.getAccessCount());

        store.getById(entry.getId());
        assertEquals(2, entry.getAccessCount());
    }

    private MemoryEntry createEntry(String userId, String content, String tag) {
        return MemoryEntry.builder()
                .userId(userId)
                .type(MemoryType.LONG_TERM)
                .content(content)
                .tag(tag)
                .build();
    }
}
