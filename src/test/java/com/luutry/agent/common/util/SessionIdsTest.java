package com.luutry.agent.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionIds Tests")
class SessionIdsTest {

    @Test
    @DisplayName("newId - 带 sess_ 前缀且格式合法")
    void testNewId() {
        String id = SessionIds.newId();
        assertTrue(id.startsWith("sess_"));
        assertEquals(37, id.length());
        assertTrue(SessionIds.isValid(id));
    }

    @Test
    @DisplayName("isValid - 拒绝空值与含冒号的 key 注入")
    void testRejectsInvalid() {
        assertFalse(SessionIds.isValid(null));
        assertFalse(SessionIds.isValid(""));
        assertFalse(SessionIds.isValid("bad:id"));
        assertFalse(SessionIds.isValid("bad id"));
        assertFalse(SessionIds.isValid("x".repeat(65)));
    }

    @Test
    @DisplayName("isValid - 兼容历史客户端生成的会话标识")
    void testAcceptsLegacyFormat() {
        assertTrue(SessionIds.isValid("session1"));
        assertTrue(SessionIds.isValid("sess_1700000000000_abc123"));
    }
}
