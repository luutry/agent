package com.luutry.agent.security.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockUserDirectory 单元测试：验证数据权限范围解析与默认拒绝（R2）。
 */
class MockUserDirectoryTest {

    private final MockUserDirectory directory = new MockUserDirectory();

    @Test
    void resolve_companyManager_hasAllDepartments() {
        UserContext ctx = directory.resolve("manager_001");
        assertEquals(3, ctx.getManagedOrgIds().size());
        assertTrue(ctx.getPermissions().contains("team:view"));
    }

    @Test
    void resolve_deptManager_scopedToOwnDepartment() {
        UserContext ctx = directory.resolve("manager_tech");
        assertEquals(List.of("DEPT_TECH"), ctx.getManagedOrgIds());
    }

    @Test
    void resolve_unknownUser_emptyScopeNoPermissions() {
        UserContext ctx = directory.resolve("ghost");
        assertTrue(ctx.getManagedOrgIds().isEmpty());
        assertTrue(ctx.getPermissions().isEmpty());
    }

    @Test
    void resolve_nullUserId_minimalContext() {
        UserContext ctx = directory.resolve(null);
        assertTrue(ctx.getManagedOrgIds().isEmpty());
    }
}
