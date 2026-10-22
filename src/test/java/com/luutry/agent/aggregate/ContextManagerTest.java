package com.luutry.agent.aggregate;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.ConversationTurn;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import com.luutry.agent.rag.RagService;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.tool.dispatcher.ToolDispatcher;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContextManager.
 */
@ExtendWith(MockitoExtension.class)
class ContextManagerTest {

    @Mock
    private ToolDispatcher toolDispatcher;

    @Mock
    private RagService ragService;

    @Mock
    private MemoryManager memoryManager;

    private ContextManager contextManager;

    @BeforeEach
    void setUp() {
        ContextAggregator aggregator = new DefaultContextAggregator();
        contextManager = new ContextManager(aggregator, toolDispatcher, ragService, memoryManager);
    }

    @Test
    void buildContext_allSources_aggregates() {
        ToolResult tool = ToolResult.success(Map.of("name", "张三"));
        RetrievalResult rag = RetrievalResult.builder()
                .chunk(TextChunk.builder().content("考勤制度").build())
                .score(0.9)
                .build();
        MemoryEntry memory = MemoryEntry.builder()
                .userId("u1").content("偏好数据").tag("pref").build();
        ConversationTurn turn = ConversationTurn.builder()
                .turnNumber(1).userMessage("hi").assistantMessage("hello").build();

        AggregatedContext ctx = contextManager.buildContext(
                List.of(tool), List.of(rag), List.of(memory), List.of(turn));

        assertEquals(4, ctx.getSegmentCount());
        assertTrue(ctx.getTotalTokens() > 0);
    }

    @Test
    void buildContext_nullInputs_treatedAsEmpty() {
        AggregatedContext ctx = contextManager.buildContext(null, null, null, null);
        assertTrue(ctx.isEmpty());
    }

    @Test
    void buildFromTool_success_includesResult() {
        ToolRequest request = ToolRequest.builder().toolName("employee_search").build();
        when(toolDispatcher.dispatch(request))
                .thenReturn(ToolResult.success(Map.of("count", 5)));

        AggregatedContext ctx = contextManager.buildFromTool(request);

        assertEquals(1, ctx.getSegmentCount());
        assertEquals(DataSource.TOOL, ctx.getSegments().get(0).getSource());
    }

    @Test
    void buildFromTool_failure_returnsEmpty() {
        ToolRequest request = ToolRequest.builder().toolName("broken_tool").build();
        when(toolDispatcher.dispatch(request))
                .thenReturn(ToolResult.fail("tool error"));

        AggregatedContext ctx = contextManager.buildFromTool(request);

        assertTrue(ctx.isEmpty());
    }

    @Test
    void buildFromRag_includesResults() {
        RetrievalResult rag = RetrievalResult.builder()
                .chunk(TextChunk.builder().content("绩效管理制度内容").build())
                .score(0.85)
                .provenance("[绩效制度v2.0]")
                .build();
        when(ragService.retrieve("绩效制度")).thenReturn(List.of(rag));

        AggregatedContext ctx = contextManager.buildFromRag("绩效制度");

        assertEquals(1, ctx.getSegmentCount());
        assertEquals(DataSource.RAG, ctx.getSegments().get(0).getSource());
    }

    @Test
    void buildFromMemory_includesEntries() {
        MemoryEntry entry = MemoryEntry.builder()
                .userId("u1").content("用户偏好查看技术部").tag("preference").build();
        when(memoryManager.retrieveRelevant(eq("u1"), anyString(), anyInt()))
                .thenReturn(List.of(entry));

        AggregatedContext ctx = contextManager.buildFromMemory("u1", "偏好");

        assertEquals(1, ctx.getSegmentCount());
        assertEquals(DataSource.MEMORY, ctx.getSegments().get(0).getSource());
    }

    @Test
    void buildSessionContext_combinesMemoryAndTurns() {
        ConversationTurn turn = ConversationTurn.builder()
                .turnNumber(1).userMessage("查人").assistantMessage("好的").build();
        MemoryManager.SessionContext sessionCtx = mock(MemoryManager.SessionContext.class);
        when(sessionCtx.getRecentTurns()).thenReturn(List.of(turn));
        when(memoryManager.getSessionContext("s1")).thenReturn(sessionCtx);
        when(memoryManager.retrieveRelevant(eq("u1"), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        AggregatedContext ctx = contextManager.buildSessionContext("s1", "u1", "查人");

        assertEquals(1, ctx.getSegmentCount());
    }

    @Test
    void aggregateSegments_delegatesToAggregator() {
        List<ContextSegment> segments = List.of(
                ContextSegment.fromTool("data", 1.0, "Tool:t1"),
                ContextSegment.fromRag("doc", 0.8, "[doc]")
        );

        AggregatedContext ctx = contextManager.aggregateSegments(segments);

        assertEquals(2, ctx.getSegmentCount());
    }

    @Test
    void buildPromptContext_emptyContext_returnsEmptyString() {
        AggregatedContext ctx = AggregatedContext.builder().build();
        assertEquals("", contextManager.buildPromptContext(ctx));
    }

    @Test
    void buildPromptContext_nullContext_returnsEmptyString() {
        assertEquals("", contextManager.buildPromptContext(null));
    }

    @Test
    void buildPromptContext_withSegments_returnsFormattedString() {
        List<ContextSegment> segments = List.of(
                ContextSegment.fromTool("员工张三绩效A级", 1.0, "Tool:perf")
        );
        AggregatedContext ctx = contextManager.aggregateSegments(segments);

        String prompt = contextManager.buildPromptContext(ctx);

        assertNotNull(prompt);
        assertTrue(prompt.contains("员工张三绩效A级"));
        assertTrue(prompt.contains("Tool业务数据库查询"));
    }
}
