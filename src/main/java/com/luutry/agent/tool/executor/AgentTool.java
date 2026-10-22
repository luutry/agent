package com.luutry.agent.tool.executor;

import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;

/**
 * Agent工具实现接口
 * <p>
 * 用途：定义所有HR工具的统一执行契约，5个内置工具（人员检索/团队指标/员工档案/绩效记录/组织树）各自实现此接口。
 * 处理问题：不同工具的执行逻辑各异，ToolExecutor需要统一调度入口，
 * 缺少接口抽象会导致Executor对每个工具做if-else分支判断，违反开闭原则。
 * 处理方式：
 * 1. 每个工具实现类加@Component注解，Spring容器自动扫描注册；
 * 2. 实现execute方法，接收ToolRequest返回ToolResult；
 * 3. 实现getToolName方法返回工具唯一标识，供Registry注册使用；
 * 4. ToolExecutor通过接口统一调用，无需关心具体实现。
 * </p>
 */
public interface AgentTool {

    /**
     * 获取工具的唯一名称，用于注册和调度
     *
     * @return 工具名称标识符
     */
    String getToolName();

    /**
     * 使用给定请求参数执行工具
     *
     * @param request 包含参数的工具调用请求
     * @return 包含结构化数据的工具执行结果
     */
    ToolResult execute(ToolRequest request);
}
