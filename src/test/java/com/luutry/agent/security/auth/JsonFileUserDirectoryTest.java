package com.luutry.agent.security.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonFileUserDirectory 单元测试：验证从 JSON 文件加载用户权限映射。
 */
@DisplayName("JsonFileUserDirectory Tests")
class JsonFileUserDirectoryTest {

    private final JsonFileUserDirectory directory = new JsonFileUserDirectory();

    @Test
    void resolve_companyManager_hasAllDepartments() {
        UserContext ctx = directory.resolve("manager_001");
        assertEquals(3, ctx.getManagedOrgIds().size());
        assertEquals("hr_admin", ctx.getRole());
        assertTrue(ctx.getPermissions().contains("team:view"));
    }

    @Test
    void resolve_deptManager_scopedToOwnDepartment() {
        UserContext ctx = directory.resolve("manager_tech");
        assertEquals(List.of("DEPT_TECH"), ctx.getManagedOrgIds());
        assertEquals("manager", ctx.getRole());
    }

    @Test
    void resolve_hrManager_scopedToHr() {
        UserContext ctx = directory.resolve("manager_hr");
        assertEquals(List.of("DEPT_HR"), ctx.getManagedOrgIds());
    }

    @Test
    void resolve_finManager_scopedToFin() {
        UserContext ctx = directory.resolve("manager_fin");
        assertEquals(List.of("DEPT_FIN"), ctx.getManagedOrgIds());
    }

    @Test
    void resolve_unknownUser_emptyScopeNoPermissions() {
        UserContext ctx = directory.resolve("ghost_user");
        assertTrue(ctx.getManagedOrgIds().isEmpty());
        assertTrue(ctx.getPermissions().isEmpty());
        assertEquals("unknown", ctx.getRole());
    }

    @Test
    void resolve_nullUserId_minimalContext() {
        UserContext ctx = directory.resolve(null);
        assertTrue(ctx.getManagedOrgIds().isEmpty());
        assertEquals("", ctx.getUserId());
    }

    @Test
    void resolve_blankUserId_minimalContext() {
        UserContext ctx = directory.resolve("  ");
        assertTrue(ctx.getManagedOrgIds().isEmpty());
    }
}
