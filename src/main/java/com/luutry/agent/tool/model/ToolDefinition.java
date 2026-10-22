package com.luutry.agent.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 工具元信息定义
 * <p>
 * 用途：描述一个Agent工具的名称、功能说明、入参JSON Schema和所需权限标签。
 * 处理问题：ToolRegistry需要统一管理所有工具的元信息，供Planner查询可用工具列表、
 * 校验调用参数、检查用户权限；缺少统一元信息定义会导致工具管理散乱。
 * 处理方式：通过不可变对象封装工具定义，包含name（唯一标识）、description（功能描述）、
 * inputSchema（入参字段定义及类型描述）、requiredPermissions（调用此工具所需的权限标签集合）；
 * 注册到Registry后，Dispatcher在调度时读取元信息做校验。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /**
     * 工具唯一标识符（如 "employee_search"、"team_metrics"）
     */
    private String name;

    /**
     * 工具功能的人类可读描述
     */
    private String description;

    /**
     * 入参Schema：参数名到类型描述的映射。
     * 供ToolInputValidator校验ToolRequest参数使用。
     * 示例：{"department": "string, required", "page": "int, optional"}
     */
    @Builder.Default
    private Map<String, String> inputSchema = Collections.emptyMap();

    /**
     * 调用此工具所需的权限标签集合。
     * 用户必须拥有全部列出的权限（AND逻辑）。
     * 示例：{"employee:view"} 或 {"employee:view", "employee:export"}
     */
    @Builder.Default
    private Set<String> requiredPermissions = Collections.emptySet();
}
