package com.luutry.agent.security.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FunctionPermissionChecker
 */
@DisplayName("FunctionPermissionChecker Tests")
class FunctionPermissionCheckerTest {

    private final FunctionPermissionChecker checker = new FunctionPermissionChecker();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("checkPermission should pass when user has the permission")
    void testCheckPermissionPass() {
        UserContextHolder.set(UserContext.builder()
                .userId("U001")
                .permissions(Set.of("employee:view", "employee:export"))
                .build());

        assertDoesNotThrow(() -> checker.checkPermission("employee:view"));
    }

    @Test
    @DisplayName("checkPermission should throw when user lacks the permission")
    void testCheckPermissionThrow() {
        UserContextHolder.set(UserContext.builder()
                .userId("U001")
                .permissions(Set.of("employee:view"))
                .build());

        PermissionDeniedException ex = assertThrows(PermissionDeniedException.class,
                () -> checker.checkPermission("employee:export"));
        assertTrue(ex.getReason().contains("employee:export"));
    }

    @Test
    @DisplayName("checkPermission should throw when no user context")
    void testCheckPermissionNoContext() {
        assertThrows(PermissionDeniedException.class,
                () -> checker.checkPermission("employee:view"));
    }

    @Test
    @DisplayName("checkAnyPermission should pass when user has at least one")
    void testCheckAnyPermissionPass() {
        UserContextHolder.set(UserContext.builder()
                .userId("U001")
                .permissions(Set.of("employee:view"))
                .build());

        assertDoesNotThrow(() -> checker.checkAnyPermission(Set.of("employee:view", "employee:export")));
    }

    @Test
    @DisplayName("checkAnyPermission should throw when user has none of the required")
    void testCheckAnyPermissionThrow() {
        UserContextHolder.set(UserContext.builder()
                .userId("U001")
                .permissions(Set.of("employee:view"))
                .build());

        assertThrows(PermissionDeniedException.class,
                () -> checker.checkAnyPermission(Set.of("employee:export", "employee:delete")));
    }

    @Test
    @DisplayName("hasPermission should return true/false correctly")
    void testHasPermission() {
        UserContextHolder.set(UserContext.builder()
                .userId("U001")
                .permissions(Set.of("employee:view"))
                .build());

        assertTrue(checker.hasPermission("employee:view"));
        assertFalse(checker.hasPermission("employee:export"));
    }

    @Test
    @DisplayName("hasPermission should return false when no context")
    void testHasPermissionNoContext() {
        assertFalse(checker.hasPermission("employee:view"));
    }
}
