package com.luutry.agent.aggregate;

import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.tool.model.ToolResult;

import java.util.List;

/**
 * 上下文聚合器接口，负责将多源数据合并为统一上下文。
 * <p>
 * 用途：定义将Tool结果、RAG片段、Memory条目和近期对话轮次
 * 聚合为单一AggregatedContext的契约。
 * 处理问题：Planner从多个数据源产生数据；上下文聚合器必须将它们合并、排序、去重并裁剪以适应Token预算。
 * 处理方式：单一aggregate方法接受所有数据源类型，返回预算受控的上下文。
 * </p>
 */
public interface ContextAggregator {

    /**
     * 将所有数据源聚合为统一上下文。
     *
     * @param toolResults   Tool执行结果（可为空）
     * @param ragResults    RAG检索结果（可为空）
     * @param memoryEntries Memory检索结果（可为空）
     * @param recentTurns   近期对话轮次，用于会话上下文（可为空）
     * @return 经过排序、去重和预算裁剪的AggregatedContext
     */
    AggregatedContext aggregate(
            List<ToolResult> toolResults,
            List<RetrievalResult> ragResults,
            List<MemoryEntry> memoryEntries,
            List<ConversationTurn> recentTurns
    );

    /**
     * 聚合预构建的上下文片段（已从数据源转换）。
     *
     * @param segments 待合并和裁剪的上下文片段列表
     * @return 经过排序、去重和预算裁剪的AggregatedContext
     */
    AggregatedContext aggregateSegments(List<ContextSegment> segments);
}
