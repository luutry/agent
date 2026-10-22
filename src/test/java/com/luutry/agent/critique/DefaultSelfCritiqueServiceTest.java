package com.luutry.agent.critique;

import com.luutry.agent.common.enums.DataSource;
import com.luutry.agent.aggregate.model.AggregatedContext;
import com.luutry.agent.aggregate.model.ContextSegment;
import com.luutry.agent.aggregate.model.Fact;
import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.critique.model.CritiqueResult;
import com.luutry.agent.critique.model.CritiqueVerdict;
import com.luutry.agent.planner.model.AgentResponse;
import com.luutry.agent.preprocessor.model.ProcessedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultSelfCritiqueService.
 */
class DefaultSelfCritiqueServiceTest {

    private DefaultSelfCritiqueService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSelfCritiqueService();
    }

    @Test
    void critique_nullResponse_returnsReject() {
        CritiqueResult result = service.critique(null, AggregatedContext.builder().build(), null);
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict());
        assertTrue(result.hasIssues());
    }

    @Test
    void critique_emptyAnswer_returnsReject() {
        AgentResponse response = AgentResponse.success("", "task1", 1);
        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), null);
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict());
    }

    @Test
    void critique_answerWithContextSupported_passes() {
        // Context contains the information
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool("员工张三的绩效评分为A级，表现优秀", 1.0, "Tool:perf")
                ))
                .totalTokens(20)
                .build();

        // Answer is supported by context
        AgentResponse response = AgentResponse.success("员工张三的绩效评分为A级，表现优秀", "task1", 1);
        ProcessedQuery query = new ProcessedQuery("查看张三的绩效");
        query.addEntity("张三");
        query.addEntity("绩效");

        CritiqueResult result = service.critique(response, context, query);
        assertEquals(CritiqueVerdict.PASS, result.getVerdict());
        assertTrue(result.getHallucinationScore() >= 0.5);
    }

    @Test
    void critique_hallucinatedAnswer_rejected() {
        // Empty context - no supporting data
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of())
                .totalTokens(0)
                .build();

        // Answer contains specific numeric data not in context
        AgentResponse response = AgentResponse.success("技术部有150人，离职率30%，平均绩效B级", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict());
        assertEquals(0.0, result.getHallucinationScore());
        assertNotNull(result.getRevisedAnswer());
    }

    @Test
    void critique_missingEntities_marksIncomplete() {
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool("技术部有50人", 1.0, "Tool:team")
                ))
                .totalTokens(10)
                .build();

        // Answer doesn't mention the queried entity "产品部"
        AgentResponse response = AgentResponse.success("技术部有50人", "task1", 1);
        ProcessedQuery query = new ProcessedQuery("查看技术部和产品部的人数");
        query.addEntity("技术部");
        query.addEntity("产品部");
        query.addEntity("人数");

        CritiqueResult result = service.critique(response, context, query);
        assertTrue(result.isIncomplete());
        assertTrue(result.hasIssues());
    }

    @Test
    void critique_noContext_noSpecificData_passes() {
        // Generic answer without specific data values
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of())
                .totalTokens(0)
                .build();

        AgentResponse response = AgentResponse.success("您好，请问有什么可以帮助您的？", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        assertEquals(CritiqueVerdict.PASS, result.getVerdict());
        assertEquals(1.0, result.getHallucinationScore());
    }

    @Test
    void critique_conflictingNumericValues_flagsConflict() {
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool("离职率为5%", 1.0, "Tool:metrics"),
                        ContextSegment.fromRag("离职率为15%", 0.8, "[报告]")
                ))
                .totalTokens(10)
                .build();

        AgentResponse response = AgentResponse.success("离职率为5%", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        assertTrue(result.isHasConflict());
    }

    @Test
    void critique_warningVerdict_whenMinorIssues() {
        // Context partially supports the answer
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool("技术部有50人，其中开发人员30人", 1.0, "Tool:team")
                ))
                .totalTokens(15)
                .build();

        // Answer mostly supported but has one unsupported sentence
        AgentResponse response = AgentResponse.success("技术部有50人。其中开发人员30人。平均年龄28岁", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        // Should be WARNING since some content is unsupported but not fully hallucinated
        assertTrue(result.getVerdict() == CritiqueVerdict.WARNING || result.getVerdict() == CritiqueVerdict.PASS);
    }

    @Test
    void critique_passResult_hasNoIssues() {
        CritiqueResult result = CritiqueResult.pass();
        assertEquals(CritiqueVerdict.PASS, result.getVerdict());
        assertEquals(1.0, result.getConfidence());
        assertFalse(result.hasIssues());
    }

    @Test
    void critique_rejectResult_hasRevisedAnswer() {
        CritiqueResult result = CritiqueResult.reject("hallucination", "安全回复");
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict());
        assertEquals("安全回复", result.getRevisedAnswer());
        assertTrue(result.hasIssues());
    }

    /**
     * 回归测试：LLM将原始工具数据改写为自然语言后，数值验证应通过。
     * 场景：上下文为 "department=技术部; turnoverRate=0.05" 格式，
     * 回答为 LLM 改写的 "各部门离职率如下：技术部离职率为5%"。
     */
    @Test
    void critique_llmRephrasedAnswerWithNumericValues_passes() {
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool(
                                "department=技术部; turnoverRate=0.05; headcount=150", 1.0, "Tool:team_metrics"),
                        ContextSegment.fromTool(
                                "department=产品部; turnoverRate=0.03; headcount=80", 1.0, "Tool:team_metrics")
                ))
                .totalTokens(30)
                .build();

        // LLM 将原始数据改写为自然语言
        AgentResponse response = AgentResponse.success(
                "各部门离职率如下：技术部离职率为5%，产品部离职率为3%", "task1", 2);

        ProcessedQuery query = new ProcessedQuery("本月各部门离职率是多少");
        query.addEntity("离职率");
        query.addEntity("部门");

        CritiqueResult result = service.critique(response, context, query);
        assertNotEquals(CritiqueVerdict.REJECT, result.getVerdict(),
                "LLM改写的回答包含正确数值，不应被REJECT");
        assertTrue(result.getHallucinationScore() >= 0.5);
    }

    /**
     * 回归测试：回答中数值与上下文不匹配时应被拒绝。
     */
    @Test
    void critique_wrongNumericValues_rejected() {
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool(
                                "department=技术部; turnoverRate=0.05", 1.0, "Tool:team_metrics")
                ))
                .totalTokens(10)
                .build();

        // 回答中的数值与上下文不匹配（上下文5%但回答写成50%）
        AgentResponse response = AgentResponse.success(
                "技术部离职率为50%", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict(),
                "回答数值与上下文不匹配，应被REJECT");
    }

    /**
     * 回归测试：LLM 改写但保留共享实体与数值时，经文本/bigram 与数值验证应通过。
     */
    @Test
    void critique_rephrasedAnswerSharingEntities_passes() {
        AggregatedContext context = AggregatedContext.builder()
                .segments(List.of(
                        ContextSegment.fromTool(
                                "员工张三，部门：技术部，职级：P7，入职时间：2023年", 1.0, "Tool:employee")
                ))
                .totalTokens(20)
                .build();

        // LLM 改写但保留了关键实体
        AgentResponse response = AgentResponse.success(
                "张三属于技术部，职级P7", "task1", 1);

        CritiqueResult result = service.critique(response, context, query());
        assertNotEquals(CritiqueVerdict.REJECT, result.getVerdict(),
                "回答与上下文共享实体与数值，不应被REJECT");
    }

    /**
     * 事实校验：答案把值归给错误实体（张冠李戴）必须被拒（R5）。
     * 3% 确在事实集合中（属财务部），但归给技术部（实际 8%）属幻觉。
     */
    @Test
    void critique_misattributedValue_rejected() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL"),
                new Fact("人力资源部", "turnoverRate", "0.05", "TOOL"),
                new Fact("财务部", "turnoverRate", "0.03", "TOOL")));

        AgentResponse response = AgentResponse.builder()
                .answer("技术部离职率为3%")
                .taskId("task1").success(true).totalRounds(1)
                .factSet(facts)
                .build();

        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), query());
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict(),
                "数字对、归属错的张冠李戴必须REJECT");
    }

    /**
     * 事实校验：模板渲染的真实数据（值与归属都对）必须通过。
     */
    @Test
    void critique_correctFactAttribution_passes() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL"),
                new Fact("人力资源部", "turnoverRate", "0.05", "TOOL"),
                new Fact("财务部", "turnoverRate", "0.03", "TOOL")));

        AgentResponse response = AgentResponse.builder()
                .answer("离职率：技术部 8%、人力资源部 5%、财务部 3%")
                .taskId("task1").success(true).totalRounds(1)
                .factSet(facts)
                .build();

        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), query());
        assertEquals(CritiqueVerdict.PASS, result.getVerdict());
        assertEquals(1.0, result.getHallucinationScore());
    }

    /**
     * N5 回归：模板路径携带合法 usedFactKeys 时，校验走零解析集合比对→PASS。
     */
    @Test
    void critique_templateWithValidFactKeys_passes() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL"),
                new Fact("财务部", "turnoverRate", "0.03", "TOOL")));

        AgentResponse response = AgentResponse.builder()
                .answer("离职率：技术部 8%、财务部 3%")
                .taskId("task1").success(true).totalRounds(1)
                .factSet(facts)
                .usedFactKeys(java.util.Set.of("技术部#turnoverRate", "财务部#turnoverRate"))
                .build();

        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), query());
        assertEquals(CritiqueVerdict.PASS, result.getVerdict());
        assertEquals(1.0, result.getHallucinationScore());
    }

    /**
     * N5 回归：usedFactKeys 包含不属于 FactSet 的键（渲染与事实不一致）→REJECT。
     */
    @Test
    void critique_templateWithUnknownFactKey_rejected() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL")));

        AgentResponse response = AgentResponse.builder()
                .answer("离职率：技术部 8%、销售部 6%")
                .taskId("task1").success(true).totalRounds(1)
                .factSet(facts)
                .usedFactKeys(java.util.Set.of("技术部#turnoverRate", "销售部#turnoverRate"))
                .build();

        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), query());
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict(), "渲染绑定了不存在的事实键必须REJECT");
    }

    private ProcessedQuery query() {
        return new ProcessedQuery("测试查询");
    }

    /**
     * N1 回归：多句答案里"两条真数据夹一条编造"必须被拒，不得因支持比例达阈而放行。
     * 旧逻辑 2/3=0.667>0.5 会 PASS；hard-fail 后必 REJECT。
     */
    @Test
    void critique_oneFabricatedSentenceAmongTrue_rejected() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL"),
                new Fact("人力资源部", "turnoverRate", "0.05", "TOOL"),
                new Fact("财务部", "turnoverRate", "0.03", "TOOL")));

        AgentResponse response = AgentResponse.builder()
                .answer("技术部离职率8%。人力资源部离职率5%。财务部离职率9%。")
                .taskId("task1").success(true).totalRounds(1)
                .factSet(facts)
                .build();

        CritiqueResult result = service.critique(response, AggregatedContext.builder().build(), query());
        assertEquals(CritiqueVerdict.REJECT, result.getVerdict(),
                "三句里一句编造（9%不在事实集）必须REJECT，不得比例放行");
        assertEquals(0.0, result.getHallucinationScore());
    }
}
