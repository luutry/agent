package com.luutry.agent.planner;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.FactExtractor;
import com.luutry.agent.memory.MemoryManager;
import com.luutry.agent.memory.model.MemoryEntry;
import com.luutry.agent.memory.model.MemoryType;
import com.luutry.agent.planner.decision.RuleBasedDecisionMaker;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.planner.react.ReActEngine;
import com.luutry.agent.planner.synthesis.DataTemplateRenderer;
import com.luutry.agent.planner.task.DefaultTaskDecomposer;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import com.luutry.agent.preprocessor.model.QueryIntent;
import com.luutry.agent.preprocessor.model.SubTask;
import com.luutry.agent.rag.RagService;
import com.luutry.agent.rag.model.RetrievalResult;
import com.luutry.agent.rag.model.TextChunk;
import com.luutry.agent.security.filter.ToolResultSanitizer;
import com.luutry.agent.tool.builtin.MockHrDataProvider;
import com.luutry.agent.tool.dispatcher.ToolDispatcher;
import com.luutry.agent.tool.model.ToolRequest;
import com.luutry.agent.tool.model.ToolResult;
import com.luutry.agent.tool.param.ToolParameterExtractor;
import com.luutry.agent.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerService with Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    @Mock
    private ToolDispatcher toolDispatcher;

    @Mock
    private RagService ragService;

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private LlmService llmService;

    private PlannerService plannerService;

    @BeforeEach
    void setUp() {
        com.luutry.agent.repository.mock.JsonEmployeeMapper employeeMapper =
                new com.luutry.agent.repository.mock.JsonEmployeeMapper();
        com.luutry.agent.repository.mock.JsonDepartmentMapper departmentMapper =
                new com.luutry.agent.repository.mock.JsonDepartmentMapper();
        com.luutry.agent.repository.mock.JsonPerformanceRecordMapper performanceRecordMapper =
                new com.luutry.agent.repository.mock.JsonPerformanceRecordMapper(employeeMapper);
        com.luutry.agent.tool.builtin.MockHrDataProvider mockHrDataProvider =
                new com.luutry.agent.tool.builtin.MockHrDataProvider(
                        employeeMapper, departmentMapper, performanceRecordMapper);

        ReActEngine engine = new ReActEngine(
                new RuleBasedDecisionMaker(),
                toolDispatcher,
                ragService,
                memoryManager,
                new ToolParameterExtractor(mockHrDataProvider),
                new ToolResultSanitizer()
        );
        plannerService = new PlannerService(new DefaultTaskDecomposer(), engine, llmService,
                new FactExtractor(), new DataTemplateRenderer());
    }

    @Test
    void plan_nullQuery_returnsFailure() {
        AgentResponse response = plannerService.plan(null, "user1", "session1");
        assertFalse(response.isSuccess());
    }

    @Test
    void plan_emptyQuery_returnsFailure() {
        ProcessedQuery pq = new ProcessedQuery("");
        pq.setIntent(QueryIntent.CHITCHAT);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertFalse(response.isSuccess());
    }

    @Test
    void plan_chitchatIntent_returnsDirectAnswer() {
        ProcessedQuery pq = new ProcessedQuery("你好");
        pq.setIntent(QueryIntent.CHITCHAT);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertTrue(response.isSuccess());
        assertNotNull(response.getAnswer());
        assertFalse(response.getSteps().isEmpty());
    }

    @Test
    void plan_toolIntent_callsTool() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.success(Map.of("employees", "张三, 李四")));

        ProcessedQuery pq = new ProcessedQuery("查看员工绩效");
        pq.setIntent(QueryIntent.TOOL);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertTrue(response.isSuccess());
        assertTrue(response.getDataSources().contains(DataSource.TOOL));
    }

    @Test
    void plan_ragIntent_callsRag() {
        TextChunk chunk = TextChunk.builder()
                .id("chunk-1")
                .content("Mock RAG result for: 离职制度")
                .tokenCount(10)
                .build();
        RetrievalResult ragResult = RetrievalResult.builder()
                .chunk(chunk)
                .score(0.9)
                .docTitle("MockDoc")
                .provenance("[MockDoc v1.0, 第一章]")
                .build();

        when(ragService.retrieve(anyString())).thenReturn(List.of(ragResult));

        ProcessedQuery pq = new ProcessedQuery("离职制度怎么规定");
        pq.setIntent(QueryIntent.RAG);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertTrue(response.isSuccess());
        assertTrue(response.getDataSources().contains(DataSource.RAG));
    }

    @Test
    void plan_responseContainsSteps() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.success(Map.of("count", 10)));

        ProcessedQuery pq = new ProcessedQuery("查看团队");
        pq.setIntent(QueryIntent.TOOL);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertFalse(response.getSteps().isEmpty());
        assertTrue(response.getTotalRounds() > 0);
    }

    @Test
    void plan_toolFails_fallbackToRag() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.fail("Tool error"));
        TextChunk chunk = TextChunk.builder()
                .id("chunk-1")
                .content("Fallback RAG result")
                .tokenCount(5)
                .build();
        RetrievalResult ragResult = RetrievalResult.builder()
                .chunk(chunk)
                .score(0.5)
                .build();
        when(ragService.retrieve(anyString())).thenReturn(List.of(ragResult));

        ProcessedQuery pq = new ProcessedQuery("查看绩效");
        pq.setIntent(QueryIntent.TOOL);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        // Should still succeed via RAG fallback
        assertTrue(response.isSuccess());
    }

    @Test
    void plan_memoryRetrieval_includesMemorySource() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.fail("no tool"));
        when(ragService.retrieve(anyString())).thenReturn(Collections.emptyList());

        MemoryEntry entry = MemoryEntry.builder()
                .id("mem-1")
                .userId("user1")
                .type(MemoryType.LONG_TERM)
                .content("Previous query result")
                .createdAt(Instant.now())
                .build();
        when(memoryManager.retrieveRelevant(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(entry));

        ProcessedQuery pq = new ProcessedQuery("查看绩效");
        pq.setIntent(QueryIntent.TOOL);
        AgentResponse response = plannerService.plan(pq, "user1", "session1");
        assertTrue(response.isSuccess());
        assertTrue(response.getDataSources().contains(DataSource.MEMORY));
    }

    @Test
    void plan_toolObservation_containsNoRawPiiOrPerformance() {
        Map<String, Object> employee = new LinkedHashMap<>();
        employee.put("name", "张三");
        employee.put("employeeId", "E001");
        employee.put("phone", "13812345678");
        employee.put("email", "zhangsan@company.com");
        employee.put("performanceGrade", "A");
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.success(Map.of("employees", List.of(employee))));
        when(llmService.generateAnswer(anyString(), anyList(), anyString())).thenReturn("已找到员工");

        ProcessedQuery pq = new ProcessedQuery("看看张三的档案");
        pq.setIntent(QueryIntent.TOOL);
        plannerService.plan(pq, "user1", "session1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateAnswer(anyString(), captor.capture(), anyString());
        String prompt = String.join("\n", captor.getValue());

        assertFalse(prompt.contains("13812345678"), "手机号不得进入 LLM 观测（R3）");
        assertFalse(prompt.contains("zhangsan@company.com"), "邮箱不得进入 LLM 观测（R3）");
        assertFalse(prompt.contains("performanceGrade"), "员工级绩效字段不得进入 LLM 观测（R3.1）");
    }

    @Test
    void plan_profileQuery_dispatchesProfileToolWithEmployeeId() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.success(Map.of("name", "张三")));

        ProcessedQuery pq = new ProcessedQuery("看看张三的档案");
        pq.setIntent(QueryIntent.TOOL);
        plannerService.plan(pq, "user1", "session1");

        ArgumentCaptor<ToolRequest> captor = ArgumentCaptor.forClass(ToolRequest.class);
        verify(toolDispatcher).dispatch(captor.capture());
        assertEquals("employee_profile", captor.getValue().getToolName(), "有明确员工时应选档案工具");
        assertEquals("E001", captor.getValue().getParameters().get("employeeId"), "应按姓名解析出工号");
    }

    @Test
    void plan_compositeQuery_executesEachTaskWithOwnIntent() {
        when(toolDispatcher.dispatch(any(ToolRequest.class)))
                .thenReturn(ToolResult.success(Map.of("count", 10)));
        TextChunk chunk = TextChunk.builder()
                .id("chunk-1")
                .content("离职需提前30天申请")
                .tokenCount(5)
                .build();
        RetrievalResult ragResult = RetrievalResult.builder()
                .chunk(chunk)
                .score(0.9)
                .build();
        when(ragService.retrieve(anyString())).thenReturn(List.of(ragResult));

        ProcessedQuery pq = new ProcessedQuery("团队有多少人？离职制度怎么规定？");
        pq.setIntent(QueryIntent.MIXED);
        SubTask toolTask = new SubTask(0, "团队有多少人");
        toolTask.setIntent(QueryIntent.TOOL);
        SubTask ragTask = new SubTask(1, "离职制度怎么规定");
        ragTask.setIntent(QueryIntent.RAG);
        pq.addSubTask(toolTask);
        pq.addSubTask(ragTask);

        AgentResponse response = plannerService.plan(pq, "user1", "session1");

        assertTrue(response.getDataSources().contains(DataSource.TOOL), "TOOL 子任务应执行");
        assertTrue(response.getDataSources().contains(DataSource.RAG), "RAG 子任务应执行");
        verify(toolDispatcher).dispatch(any(ToolRequest.class));
        verify(ragService).retrieve(anyString());
    }
}
