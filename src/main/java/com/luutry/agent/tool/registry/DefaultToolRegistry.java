package com.luutry.agent.tool.registry;

import com.luutry.agent.tool.executor.AgentTool;
import com.luutry.agent.tool.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认工具注册中心实现
 * <p>
 * 用途：基于ConcurrentHashMap管理所有Agent工具的注册、注销和查询，保证线程安全。
 * 处理问题：ToolRegistry接口需要一个可独立运行的内存实现，
 * 在Spring容器启动时自动收集所有AgentTool实现并注册到中心。
 * 处理方式：
 * 1. 使用两个ConcurrentHashMap分别存储工具实例（tools）和元信息（definitions）；
 * 2. register方法同时更新两个Map；
 * 3. getAllDefinitions返回不可变列表快照；
 * 4. 重复注册同名工具会覆盖旧实现并打印警告日志。
 * </p>
 */
@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public void register(AgentTool tool, ToolDefinition definition) {
        if (tool == null || definition == null || definition.getName() == null) {
            throw new IllegalArgumentException("Tool and definition must not be null");
        }
        String name = definition.getName();
        if (tools.containsKey(name)) {
            log.warn("覆盖已存在的工具注册：{}", name);
        }
        tools.put(name, tool);
        definitions.put(name, definition);
        log.info("已注册工具：{} - {}", name, definition.getDescription());
    }

    @Override
    public void unregister(String name) {
        tools.remove(name);
        definitions.remove(name);
        log.info("已注销工具：{}", name);
    }

    @Override
    public Optional<AgentTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public Optional<ToolDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    @Override
    public List<ToolDefinition> getAllDefinitions() {
        return new ArrayList<>(definitions.values());
    }

    @Override
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
