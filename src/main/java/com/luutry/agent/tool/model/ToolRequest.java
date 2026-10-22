package com.luutry.agent.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * 工具调用请求
 * <p>
 * 用途：封装Planner发起工具调用时的请求参数，包含目标工具名称和入参Map。
 * 处理问题：Planner调用ToolDispatcher时需要传递工具名和参数，
 * 缺少统一请求封装会导致方法参数列表膨胀、格式不一致。
 * 处理方式：通过Builder模式构建，包含toolName（工具唯一标识）和
 * parameters（键值对入参，值支持String/Number/Boolean/List/Map等JSON兼容类型）；
 * ToolDispatcher接收此对象后做权限校验、入参校验、委托执行。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolRequest {

    /**
     * 目标工具名称（必须匹配已注册的ToolDefinition）
     */
    private String toolName;

    /**
     * 工具入参，键值对形式。
     * 值应为JSON兼容类型（String、Number、Boolean、List、Map）。
     */
    @Builder.Default
    private Map<String, Object> parameters = Collections.emptyMap();
}
