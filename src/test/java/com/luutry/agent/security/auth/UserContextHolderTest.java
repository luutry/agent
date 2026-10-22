package com.luutry.agent.security.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserContextHolder
 */
@DisplayName("UserContextHolder Tests")
class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should return null when no context is set")
    void testGetReturnsNullWhenNotSet() {
        assertNull(UserContextHolder.get());
    }

    @Test
    @DisplayName("Should set and get user context")
    void testSetAndGet() {
        UserContext context = UserContext.builder()
                .userId("U001")
                .role("manager")
                .managedOrgIds(List.of("ORG_A", "ORG_B"))
                .permissions(Set.of("employee:view"))
                .build();

        UserContextHolder.set(context);

        UserContext retrieved = UserContextHolder.get();
        assertNotNull(retrieved);
        assertEquals("U001", retrieved.getUserId());
        assertEquals("manager", retrieved.getRole());
        assertEquals(2, retrieved.getManagedOrgIds().size());
        assertTrue(retrieved.getPermissions().contains("employee:view"));
    }

    @Test
    @DisplayName("Should clear context properly")
    void testClear() {
        UserContextHolder.set(UserContext.builder().userId("U001").build());
        assertNotNull(UserContextHolder.get());

        UserContextHolder.clear();
        assertNull(UserContextHolder.get());
    }

    @Test
    @DisplayName("Should isolate context between threads")
    void testThreadIsolation() throws InterruptedException {
        UserContextHolder.set(UserContext.builder().userId("main-thread").build());

        Thread otherThread = new Thread(() -> {
            assertNull(UserContextHolder.get());
            UserContextHolder.set(UserContext.builder().userId("other-thread").build());
            assertEquals("other-thread", UserContextHolder.get().getUserId());
        });
        otherThread.start();
        otherThread.join();

        assertEquals("main-thread", UserContextHolder.get().getUserId());
    }

    @Test
    @DisplayName("UserContext should have default empty collections")
    void testDefaultCollections() {
        UserContext context = UserContext.builder().userId("U001").build();
        assertNotNull(context.getManagedOrgIds());
        assertNotNull(context.getPermissions());
        assertTrue(context.getManagedOrgIds().isEmpty());
        assertTrue(context.getPermissions().isEmpty());
    }
}
