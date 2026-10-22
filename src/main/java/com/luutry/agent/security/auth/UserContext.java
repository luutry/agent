package com.luutry.agent.security.auth;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用户上下文对象
 * <p>
 * 用途：封装当前请求用户的身份信息和权限范围，在整个请求链路中传递。
 * 处理问题：Agent各模块（Tool/Memory/RAG）都需要知道"当前是谁在操作"，
 * 用于数据权限过滤、功能权限校验、记忆隔离等安全判断。
 * 处理方式：由Controller层拦截器从请求头/Token中解析并设置到UserContextHolder，
 * 各业务模块通过UserContextHolder.get()获取，无需方法参数层层传递。
 * </p>
 */
@Data
@Builder
public class UserContext {

    /**
     * 用户唯一标识
     */
    private String userId;

    /**
     * 用户角色名称（如 "manager"、"hr_admin"）
     */
    private String role;

    /**
     * 用户管辖的组织ID列表（数据权限范围边界）
     */
    @Builder.Default
    private List<String> managedOrgIds = Collections.emptyList();

    /**
     * 功能权限标签集合（如 "employee:view"、"employee:export"）
     */
    @Builder.Default
    private Set<String> permissions = Collections.emptySet();
}
