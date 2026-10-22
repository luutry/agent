package com.luutry.agent.planner.synthesis;

import com.luutry.agent.aggregate.model.Fact;
import com.luutry.agent.aggregate.model.FactSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataTemplateRenderer 单元测试：验证纯数据回答的确定性模板渲染。
 */
class DataTemplateRendererTest {

    private final DataTemplateRenderer renderer = new DataTemplateRenderer();

    private FactSet turnoverFacts() {
        return FactSet.of(List.of(
                new Fact("技术部", "turnoverRate", "0.08", "TOOL"),
                new Fact("人力资源部", "turnoverRate", "0.05", "TOOL"),
                new Fact("财务部", "turnoverRate", "0.03", "TOOL")));
    }

    @Test
    void render_turnoverRate_producesPercentagePerEntity() {
        Optional<DataTemplateRenderer.RenderResult> out = renderer.render("本月各部门离职率是多少", turnoverFacts());
        assertTrue(out.isPresent());
        // N6：查询含"本月"，模板附加快照口径声明
        assertEquals("离职率：技术部 8%、人力资源部 5%、财务部 3%（当前快照，不支持按时间筛选）", out.get().text());
        assertEquals(java.util.Set.of("技术部#turnoverRate", "人力资源部#turnoverRate", "财务部#turnoverRate"),
                out.get().usedFactKeys());
    }

    @Test
    void render_noTimeWord_noDisclaimer() {
        Optional<DataTemplateRenderer.RenderResult> out = renderer.render("各部门离职率", turnoverFacts());
        assertTrue(out.isPresent());
        assertEquals("离职率：技术部 8%、人力资源部 5%、财务部 3%", out.get().text());
    }

    /**
     * N7：因果/比较类查询需解释，不走确定性模板，返回 empty 以回退 LLM。
     */
    @Test
    void render_causalQuery_returnsEmpty() {
        assertTrue(renderer.render("为什么技术部离职率最高", turnoverFacts()).isEmpty());
        assertTrue(renderer.render("对比各部门离职率", turnoverFacts()).isEmpty());
    }

    @Test
    void render_headcountMetric_appendsUnit() {
        FactSet facts = FactSet.of(List.of(
                new Fact("技术部", "currentStaff", "15", "TOOL"),
                new Fact("财务部", "currentStaff", "6", "TOOL")));
        Optional<DataTemplateRenderer.RenderResult> out = renderer.render("各部门在职人数", facts);
        assertTrue(out.isPresent());
        assertEquals("在职人数：技术部 15人、财务部 6人", out.get().text());
    }

    @Test
    void render_unknownMetric_returnsEmpty() {
        assertTrue(renderer.render("公司文化是什么", turnoverFacts()).isEmpty());
    }

    @Test
    void render_emptyFactSet_returnsEmpty() {
        assertTrue(renderer.render("各部门离职率", FactSet.empty()).isEmpty());
    }

    @Test
    void render_nullQuery_returnsEmpty() {
        assertTrue(renderer.render(null, turnoverFacts()).isEmpty());
    }
}
