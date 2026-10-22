package com.luutry.agent.security.auth;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 功能权限校验服务
 * <p>
 * 用途：校验当前用户是否拥有指定的功能权限标签（如employee:view、employee:export）。
 * 处理问题：Agent系统需要区分不同角色的操作权限，例如普通管理者只能查看，HR管理员可导出；
 * 在执行敏感操作前必须校验功能权限，无权限则拒绝并提示。
 * 处理方式：
 * 1. 从UserContextHolder获取当前用户的权限集合；
 * 2. 检查是否包含所需权限标签；
 * 3. 无权限则抛出PermissionDeniedException。
 * </p>
 */
@Component
public class FunctionPermissionChecker {

    /**
     * 校验当前用户是否拥有指定权限。
     * 未授权时抛出PermissionDeniedException。
     *
     * @param requiredPermission 待校验的权限标签（如 "employee:view"）
     */
    public void checkPermission(String requiredPermission) {
        UserContext context = UserContextHolder.get();
        if (context == null) {
            throw new PermissionDeniedException("User context not available");
        }
        Set<String> permissions = context.getPermissions();
        if (permissions == null || !permissions.contains(requiredPermission)) {
            throw new PermissionDeniedException(
                    "User [" + context.getUserId() + "] lacks permission: " + requiredPermission);
        }
    }

    /**
     * 校验当前用户是否拥有指定权限中的任意一个。
     * 用户至少拥有一个指定权限时即通过校验。
     *
     * @param requiredPermissions 权限标签集合，至少需拥有其中一个
     */
    public void checkAnyPermission(Set<String> requiredPermissions) {
        UserContext context = UserContextHolder.get();
        if (context == null) {
            throw new PermissionDeniedException("User context not available");
        }
        Set<String> userPermissions = context.getPermissions();
        if (userPermissions == null || requiredPermissions == null) {
            throw new PermissionDeniedException("No permissions available");
        }
        boolean hasAny = userPermissions.stream().anyMatch(requiredPermissions::contains);
        if (!hasAny) {
            throw new PermissionDeniedException(
                    "User [" + context.getUserId() + "] lacks any of required permissions: " + requiredPermissions);
        }
    }

    /**
     * 检查当前用户是否拥有指定权限，返回布尔结果
     *
     * @param permission 待检查的权限标签
     * @return 用户拥有该权限时返回true
     */
    public boolean hasPermission(String permission) {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getPermissions() == null) {
            return false;
        }
        return context.getPermissions().contains(permission);
    }
}
