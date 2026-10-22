package com.luutry.agent.tool.model;

import com.luutry.agent.common.enums.DataSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * 工具执行结果封装
 * <p>
 * 用途：统一封装工具执行后的返回数据，包含成功标志、结构化数据载荷、错误信息、来源标识和耗时。
 * 处理问题：各工具返回格式不一致，Planner/上下文聚合层需要统一解析；
 * 缺少来源标识无法做可信度排序；缺少耗时无法做性能监控。
 * 处理方式：通过Builder模式构建，包含success（是否成功）、data（结构化结果Map）、
 * errorMessage（失败原因）、dataSource（固定为DataSource.TOOL，最高可信度）、
 * executionTimeMs（执行耗时毫秒数）；提供静态工厂方法success(data)和fail(message)简化创建。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 数据权限截断声明的约定键：工具按 scope 过滤后，用此键回传"仅显示部分"的透明声明（复审 N4）。
     * 声明文本不含数字，以免干扰校验层的数值事实比对。
     */
    public static final String SCOPE_NOTICE_KEY = "scopeNotice";

    /**
     * 工具执行是否成功
     */
    private boolean success;

    /**
     * 结构化结果数据载荷（失败时为空Map）
     */
    @Builder.Default
    private Map<String, Object> data = Collections.emptyMap();

    /**
     * 执行失败时的错误信息（成功时为null）
     */
    private String errorMessage;

    /**
     * 数据来源标识，工具结果固定为DataSource.TOOL。
     * 供上下文聚合层按可信度优先级排序使用。
     */
    @Builder.Default
    private DataSource dataSource = DataSource.TOOL;

    /**
     * 工具执行耗时（毫秒）
     */
    private long executionTimeMs;

    /**
     * 创建带数据的成功结果
     *
     * @param data 结果数据载荷
     * @return 成功的ToolResult
     */
    public static ToolResult success(Map<String, Object> data) {
        return ToolResult.builder()
                .success(true)
                .data(data != null ? data : Collections.emptyMap())
                .build();
    }

    /**
     * 创建带数据和执行耗时的成功结果
     *
     * @param data             结果数据载荷
     * @param executionTimeMs  执行耗时（毫秒）
     * @return 成功的ToolResult
     */
    public static ToolResult success(Map<String, Object> data, long executionTimeMs) {
        return ToolResult.builder()
                .success(true)
                .data(data != null ? data : Collections.emptyMap())
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * 创建带错误信息的失败结果
     *
     * @param errorMessage 失败原因描述
     * @return 失败的ToolResult
     */
    public static ToolResult fail(String errorMessage) {
        return ToolResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
