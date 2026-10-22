package com.luutry.agent.tool.registry;

import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册中心接口
 * <p>
 * 用途：管理所有Agent工具的注册、注销和查询，提供工具发现能力供Planner使用。
 * 处理问题：Planner需要知道系统有哪些可用工具、各工具的元信息（名称/描述/入参Schema/权限），
 * 缺少统一注册中心会导致工具管理散乱、Planner无法动态发现工具。
 * 处理方式：
 * 1. register(tool, definition)：将工具实例和元信息绑定注册；
 * 2. unregister(name)：按名称注销工具；
 * 3. getTool(name)：按名称查找已注册的工具实例；
 * 4. getDefinition(name)：按名称查找工具元信息；
 * 5. getAllDefinitions()：获取全部工具元信息列表，供Planner生成工具描述。
 * </p>
 */
public interface ToolRegistry {

    /**
     * 注册工具及其元信息定义
     *
     * @param tool       工具实现实例
     * @param definition 工具元信息定义
     */
    void register(AgentTool tool, ToolDefinition definition);

    /**
     * 按名称注销工具
     *
     * @param name 待注销的工具名称
     */
    void unregister(String name);

    /**
     * 按名称获取工具实现
     *
     * @param name 工具名称
     * @return 找到时返回包含工具的Optional，否则返回空
     */
    Optional<AgentTool> getTool(String name);

    /**
     * 按名称获取工具元信息定义
     *
     * @param name 工具名称
     * @return 找到时返回包含定义的Optional，否则返回空
     */
    Optional<ToolDefinition> getDefinition(String name);

    /**
     * 获取所有已注册的工具元信息定义
     *
     * @return 所有工具定义列表
     */
    List<ToolDefinition> getAllDefinitions();

    /**
     * 检查指定名称的工具是否已注册
     *
     * @param name 待检查的工具名称
     * @return 已注册时返回true
     */
    boolean hasTool(String name);
}
