package com.luutry.agent.preprocessor.resolver;

import com.luutry.agent.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LlmCoreferenceResolver 校验层测试。
 * 覆盖：JSON 解析、置信度、实体存在性、文本完整性、长度边界。
 */
@DisplayName("LlmCoreferenceResolver 校验层测试")
class LlmCoreferenceResolverTest {

    private LlmService llmService;
    private LlmCoreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        ResolvePolicy policy = new ResolvePolicy();
        llmService = mock(LlmService.class);
        resolver = new LlmCoreferenceResolver(llmService, policy);
    }

    private void stubResponse(String response) {
        when(llmService.generate(anyString(), anyString())).thenReturn(response);
    }

    private String json(String resolvedQuery, String entity, double confidence) {
        return "{\"resolved\":true,\"resolvedQuery\":\"" + resolvedQuery + "\",\"pronouns\":["
                + "{\"pronoun\":\"他\",\"entity\":\"" + entity + "\",\"confidence\":" + confidence + "}]}";
    }

    @Test
    @DisplayName("合法 JSON 返回消解结果与代词映射")
    void resolve_validJson_returnsResolvedResult() {
        stubResponse(json("张三的绩效", "张三", 0.95));

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertTrue(result.isResolved());
        assertEquals("张三的绩效", result.getResolvedText());
        assertEquals(ResolveMethod.LLM_RESOLUTION, result.getMethod());
        assertEquals("张三", result.getResolvedPairs().get("他"));
        assertEquals(0.95, result.getConfidence(), 0.001);
    }

    @Test
    @DisplayName("markdown 代码块包裹的 JSON 可解析")
    void resolve_markdownWrappedJson_parsed() {
        stubResponse("```json\n" + json("张三的绩效", "张三", 0.9) + "\n```");

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertTrue(result.isResolved());
        assertEquals("张三的绩效", result.getResolvedText());
    }

    @Test
    @DisplayName("置信度低于阈值时降级")
    void resolve_lowConfidence_degrades() {
        stubResponse(json("张三的绩效", "张三", 0.5));

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertFalse(result.isResolved());
        assertEquals("他的绩效", result.getResolvedText());
    }

    @Test
    @DisplayName("实体不在历史中时降级")
    void resolve_entityNotInHistory_degrades() {
        stubResponse(json("李四的绩效", "李四", 0.99));

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertFalse(result.isResolved());
    }

    @Test
    @DisplayName("pronouns 为空时降级")
    void resolve_emptyPronouns_degrades() {
        stubResponse("{\"resolved\":true,\"resolvedQuery\":\"张三的绩效\",\"pronouns\":[]}");

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertFalse(result.isResolved());
    }

    @Test
    @DisplayName("非法 JSON 时降级")
    void resolve_invalidJson_degrades() {
        stubResponse("抱歉，我无法处理");

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertFalse(result.isResolved());
        assertEquals("他的绩效", result.getResolvedText());
    }

    @Test
    @DisplayName("语义被掏空的改写给拒掉（完整性校验）")
    void resolve_guttedRewrite_rejected() {
        stubResponse(json("张三的绩效", "张三", 0.99));

        ResolveResult result = resolver.resolve("那他和她的绩效对比", List.of("张三的绩效是A"));

        assertFalse(result.isResolved(), "吞掉'对比'的改写不应通过完整性校验");
        assertEquals("那他和她的绩效对比", result.getResolvedText());
    }

    @Test
    @DisplayName("扩张超过原文 3 倍的改写给拒掉")
    void resolve_overExpanded_rejected() {
        stubResponse(json("张三的绩效以及研发部门的考勤和更多内容补充说明", "张三", 0.99));

        ResolveResult result = resolver.resolve("他的绩效", List.of("张三的绩效是A"));

        assertFalse(result.isResolved());
    }
}
