package com.luutry.agent.security.auth;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * UserDirectory 的 Mock 实现（已废弃，由 JsonFileUserDirectory 替代）。
 * <p>
 * 保留作为参考，不再注册为 Spring Bean。
 * 用户权限数据已迁移至 mock/user_directory.json。
 * </p>
 * TODO(doc/TODO/010_数据权限真实模型接入.md): 真实实现应来自组织树 + 汇报关系
 */
// @Component — 已被 JsonFileUserDirectory 替代
public class MockUserDirectory implements UserDirectory {

    /** 全部门范围（公司级 HR 管理者） */
    private static final List<String> ALL_DEPARTMENTS = List.of("DEPT_TECH", "DEPT_HR", "DEPT_FIN");

    /** 管理者通用只读功能权限 */
    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "employee:view", "team:view", "org:view", "performance:view");

    @Override
    public UserContext resolve(String userId) {
        if (userId == null || userId.isBlank()) {
            return UserContext.builder().userId("").build();
        }
        return switch (userId) {
            // 公司级 HR 管理者：管辖全部部门
            case "manager_001" -> UserContext.builder()
                    .userId(userId)
                    .role("hr_admin")
                    .managedOrgIds(ALL_DEPARTMENTS)
                    .permissions(MANAGER_PERMISSIONS)
                    .build();
            // 技术部经理：仅管辖技术部（用于验证数据权限过滤）
            case "manager_tech" -> UserContext.builder()
                    .userId(userId)
                    .role("manager")
                    .managedOrgIds(List.of("DEPT_TECH"))
                    .permissions(MANAGER_PERMISSIONS)
                    .build();
            // 未识别用户：空范围、无权限，默认拒绝
            default -> UserContext.builder()
                    .userId(userId)
                    .role("unknown")
                    .build();
        };
    }
}
