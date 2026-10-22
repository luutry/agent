package com.luutry.agent.security.filter;

import java.util.List;

/**
 * 数据权限前置过滤器
 * <p>
 * 用途：在数据查询阶段就按用户管辖范围过滤数据，确保管理者只能看到管辖范围内的下属数据。
 * 处理问题：AGENTS.md要求"权限过滤前置执行，数据查询阶段就做权限过滤，禁止查询后再过滤"；
 * 本接口定义过滤契约，业务层在构建SQL或过滤结果时调用。
 * 处理方式：
 * 1. 业务数据实体实现DataScopeAware接口，暴露所属组织ID；
 * 2. 调用filter方法，传入用户管辖的组织ID列表和数据列表；
 * 3. 仅保留orgId在管辖范围内的数据，返回过滤后的列表。
 * </p>
 *
 * @param <T> 实现DataScopeAware接口的数据实体类型
 */
public class DataPermissionFilter<T extends DataPermissionFilter.DataScopeAware> {

    /**
     * 按用户管辖的组织范围过滤数据列表。
     * 仅保留用户管辖组织ID范围内的数据。
     *
     * @param managedOrgIds 用户管辖的组织ID列表
     * @param dataList      待过滤的原始数据列表
     * @return 过滤后的列表，仅包含范围内的数据
     */
    public List<T> filter(List<String> managedOrgIds, List<T> dataList) {
        if (managedOrgIds == null || managedOrgIds.isEmpty()) {
            return List.of();
        }
        if (dataList == null || dataList.isEmpty()) {
            return List.of();
        }
        return dataList.stream()
                .filter(item -> managedOrgIds.contains(item.getOrgId()))
                .toList();
    }

    /**
     * 检查单条数据是否在用户的数据范围内
     *
     * @param managedOrgIds 用户管辖的组织ID列表
     * @param item          待检查的数据项
     * @return 数据在范围内时返回true
     */
    public boolean isInScope(List<String> managedOrgIds, T item) {
        if (managedOrgIds == null || managedOrgIds.isEmpty() || item == null) {
            return false;
        }
        return managedOrgIds.contains(item.getOrgId());
    }

    /**
     * 属于某个组织范围的数据实体接口。
     * 业务实体必须实现此接口以支持数据权限过滤。
     */
    public interface DataScopeAware {

        /**
         * 获取此数据所属的组织ID
         *
         * @return 组织ID
         */
        String getOrgId();
    }
}
