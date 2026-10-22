package com.luutry.agent.aggregate;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.rag.RagService;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.tool.dispatcher.ToolDispatcher;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 上下文管理门面，提供上下文聚合的统一入口。
 * <p>
 * 用途：通过协调Tool、RAG、Memory的数据检索，构建上下文并聚合为预算受控的AggregatedContext。
 * 处理问题：Planner/反思校验层需要单一API获取完整准备好的上下文，
 * 而无需手动编排多个服务调用和聚合逻辑。
 * 处理方式：包装ContextAggregator，提供处理数据检索、缓存和会话上下文构建的便捷方法。
 * </p>
 */
@Service
public class ContextManager {

    private final ContextAggregator contextAggregator;
    private final ToolDispatcher toolDispatcher;
    private final RagService ragService;
    private final MemoryManager memoryManager;

    public ContextManager(ContextAggregator contextAggregator,
                          ToolDispatcher toolDispatcher,
                          RagService ragService,
                          MemoryManager memoryManager) {
        this.contextAggregator = contextAggregator;
        this.toolDispatcher = toolDispatcher;
        this.ragService = ragService;
        this.memoryManager = memoryManager;
    }

    /**
     * 从明确的数据源构建聚合上下文。
     *
     * @param toolResults   工具执行结果（可为null）
     * @param ragResults    RAG检索结果（可为null）
     * @param memoryEntries 记忆检索结果（可为null）
     * @param recentTurns   近期对话轮次（可为null）
     * @return Token预算内的聚合上下文
     */
    public AggregatedContext buildContext(
            List<ToolResult> toolResults,
            List<RetrievalResult> ragResults,
            List<MemoryEntry> memoryEntries,
            List<ConversationTurn> recentTurns) {

        return contextAggregator.aggregate(
                toolResults != null ? toolResults : Collections.emptyList(),
                ragResults != null ? ragResults : Collections.emptyList(),
                memoryEntries != null ? memoryEntries : Collections.emptyList(),
                recentTurns != null ? recentTurns : Collections.emptyList()
        );
    }

    /**
     * 通过执行工具调用并聚合其结果来构建上下文。
     *
     * @param toolRequest 待执行的工具请求
     * @return 工具结果的聚合上下文
     */
    public AggregatedContext buildFromTool(ToolRequest toolRequest) {
        ToolResult result = toolDispatcher.dispatch(toolRequest);
        List<ToolResult> results = result.isSuccess()
                ? List.of(result)
                : Collections.emptyList();

        return contextAggregator.aggregate(results, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 从RAG检索构建上下文。
     *
     * @param query 搜索查询
     * @return RAG结果的聚合上下文
     */
    public AggregatedContext buildFromRag(String query) {
        List<RetrievalResult> results = ragService.retrieve(query);
        return contextAggregator.aggregate(Collections.emptyList(), results,
                Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 从记忆检索构建上下文。
     *
     * @param userId 用户ID
     * @param query  搜索查询
     * @return 记忆结果的聚合上下文
     */
    public AggregatedContext buildFromMemory(String userId, String query) {
        List<MemoryEntry> entries = memoryManager.retrieveRelevant(
                userId, query, AgentConstants.DEFAULT_MEMORY_SEARCH_TOP_K);
        return contextAggregator.aggregate(Collections.emptyList(), Collections.emptyList(),
                entries, Collections.emptyList());
    }

    /**
     * 构建包含近期对话轮次的完整会话上下文。
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param query     当前查询，用于额外的记忆检索
     * @return 包含对话历史和记忆的聚合上下文
     */
    public AggregatedContext buildSessionContext(String sessionId, String userId, String query) {
        MemoryManager.SessionContext sessionCtx = memoryManager.getSessionContext(sessionId);
        List<ConversationTurn> recentTurns = sessionCtx.getRecentTurns();
        List<MemoryEntry> memoryEntries = memoryManager.retrieveRelevant(
                userId, query, AgentConstants.DEFAULT_MEMORY_SEARCH_TOP_K);

        return contextAggregator.aggregate(
                Collections.emptyList(),
                Collections.emptyList(),
                memoryEntries,
                recentTurns
        );
    }

    /**
     * 直接聚合预构建的上下文片段。
     *
     * @param segments 上下文片段列表
     * @return 聚合并经预算裁剪的上下文
     */
    public AggregatedContext aggregateSegments(List<ContextSegment> segments) {
        return contextAggregator.aggregateSegments(segments);
    }

    /**
     * 从聚合上下文构建可直接用于Prompt的字符串。
     *
     * @param aggregatedContext 聚合上下文
     * @return 包含溯源引用的格式化Prompt字符串
     */
    public String buildPromptContext(AggregatedContext aggregatedContext) {
        if (aggregatedContext == null || aggregatedContext.isEmpty()) {
            return "";
        }
        return aggregatedContext.toPromptString();
    }
}
