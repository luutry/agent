package com.luutry.agent.security.auth;

/**
 * 用户身份与数据权限范围解析器。
 * <p>
 * 用途：在请求入口把 userId 解析为 UserContext（含管辖部门 managedOrgIds 与功能权限），
 * 供 ToolDispatcher 功能权限校验与工具查询层数据权限过滤（R2）使用。
 * 处理问题：真实实现应来自组织树 + 汇报关系；当前阶段用 Mock 隔离，后续替换不动调用方。
 * </p>
 */
public interface UserDirectory {

    /**
     * 按 userId 解析用户上下文。
     *
     * @param userId 用户标识
     * @return 解析出的 UserContext；无法识别时返回仅含 userId 的最小上下文
     */
    UserContext resolve(String userId);
}
