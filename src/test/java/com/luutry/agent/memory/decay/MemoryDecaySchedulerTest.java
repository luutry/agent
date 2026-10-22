package com.luutry.agent.memory.decay;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.memory.longterm.InMemoryLongTermStore;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryDecayScheduler.
 * Covers: time decay calculation, access boost, decay cycle, forgetting threshold.
 */
@DisplayName("MemoryDecayScheduler Tests")
class MemoryDecaySchedulerTest {

    private MemoryDecayScheduler scheduler;
    private InMemoryLongTermStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryLongTermStore();
        scheduler = new MemoryDecayScheduler(store);
    }

    @Test
    @DisplayName("computeTimeDecay - fresh memory has score near 1.0")
    void testTimeDecayFresh() {
        double decay = scheduler.computeTimeDecay(Instant.now());
        assertEquals(1.0, decay, 0.001);
    }

    @Test
    @DisplayName("computeTimeDecay - older memory has lower score")
    void testTimeDecayOlder() {
        double decay1h = scheduler.computeTimeDecay(Instant.now().minus(1, ChronoUnit.HOURS));
        double decay24h = scheduler.computeTimeDecay(Instant.now().minus(24, ChronoUnit.HOURS));
        double decay168h = scheduler.computeTimeDecay(Instant.now().minus(168, ChronoUnit.HOURS));

        assertTrue(decay1h > decay24h, "1h decay should be > 24h decay");
        assertTrue(decay24h > decay168h, "24h decay should be > 168h decay");
        assertTrue(decay1h > 0.9, "1h decay should still be high");
        assertTrue(decay168h < 0.5, "168h decay should be significantly reduced");
    }

    @Test
    @DisplayName("computeAccessBoost - zero access returns 1.0")
    void testAccessBoostZero() {
        double boost = scheduler.computeAccessBoost(0);
        assertEquals(1.0, boost, 0.001);
    }

    @Test
    @DisplayName("computeAccessBoost - more access increases boost")
    void testAccessBoostIncreasing() {
        double boost1 = scheduler.computeAccessBoost(1);
        double boost5 = scheduler.computeAccessBoost(5);
        double boost20 = scheduler.computeAccessBoost(20);

        assertTrue(boost1 > 1.0);
        assertTrue(boost5 > boost1);
        assertTrue(boost20 > boost5);
    }

    @Test
    @DisplayName("recalculateDecayScore - combines time decay and access boost")
    void testRecalculateDecayScore() {
        MemoryEntry entry = MemoryEntry.builder()
                .id("test-1")
                .userId("user-1")
                .type(MemoryType.LONG_TERM)
                .content("Test content")
                .lastAccessedAt(Instant.now().minus(24, ChronoUnit.HOURS))
                .accessCount(5)
                .build();

        double score = scheduler.recalculateDecayScore(entry);

        // Should be > 0 and <= 1.0
        assertTrue(score > 0);
        assertTrue(score <= 1.0);
        // With 5 accesses, boost should partially offset 24h decay
        assertEquals(score, entry.getDecayScore());
    }

    @Test
    @DisplayName("runDecayCycle - forgets entries below threshold")
    void testRunDecayCycleForgets() {
        // Create an entry that will decay below threshold (very old, never accessed)
        MemoryEntry oldEntry = MemoryEntry.builder()
                .id("old-1")
                .userId("user-1")
                .type(MemoryType.LONG_TERM)
                .content("Very old memory")
                .lastAccessedAt(Instant.now().minus(1000, ChronoUnit.HOURS))
                .accessCount(0)
                .decayScore(0.01)
                .build();
        store.save(oldEntry);

        // Create a fresh entry that should survive
        MemoryEntry freshEntry = MemoryEntry.builder()
                .id("fresh-1")
                .userId("user-1")
                .type(MemoryType.LONG_TERM)
                .content("Fresh memory")
                .lastAccessedAt(Instant.now())
                .accessCount(10)
                .decayScore(1.0)
                .build();
        store.save(freshEntry);

        int forgotten = scheduler.runDecayCycle("user-1");

        // Old entry should be forgotten (score < threshold)
        assertTrue(forgotten >= 1);
        assertNull(store.getById("old-1"));
        // Fresh entry should survive
        assertNotNull(store.getById("fresh-1"));
    }

    @Test
    @DisplayName("runDecayCycle - removes expired entries first")
    void testRunDecayCycleRemovesExpired() {
        MemoryEntry expired = MemoryEntry.builder()
                .id("expired-1")
                .userId("user-1")
                .type(MemoryType.LONG_TERM)
                .content("Expired memory")
                .expireAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        store.save(expired);

        scheduler.runDecayCycle("user-1");

        assertNull(store.getById("expired-1"));
    }

    @Test
    @DisplayName("computeTimeDecay - future timestamp returns 1.0")
    void testTimeDecayFuture() {
        double decay = scheduler.computeTimeDecay(Instant.now().plus(1, ChronoUnit.HOURS));
        assertEquals(1.0, decay, 0.001);
    }

    @Test
    @DisplayName("decay formula verification")
    void testDecayFormula() {
        // Verify: decayScore = e^(-λ × hours) × (1 + log(1 + accessCount))
        double lambda = AgentConstants.MEMORY_DECAY_LAMBDA;
        int hours = 48;
        int accessCount = 3;

        double expectedTimeDecay = Math.exp(-lambda * hours);
        double expectedAccessBoost = 1.0 + Math.log(1.0 + accessCount);
        double expectedScore = Math.min(1.0, expectedTimeDecay * expectedAccessBoost);

        MemoryEntry entry = MemoryEntry.builder()
                .id("formula-test")
                .userId("user-1")
                .type(MemoryType.LONG_TERM)
                .content("Formula test")
                .lastAccessedAt(Instant.now().minus(hours, ChronoUnit.HOURS))
                .accessCount(accessCount)
                .build();

        double actualScore = scheduler.recalculateDecayScore(entry);
        assertEquals(expectedScore, actualScore, 0.001);
    }
}
