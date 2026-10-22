package com.luutry.agent.preprocessor.router;

import com.luutry.agent.preprocessor.model.SubTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedTaskDecomposer（TODO-009 验收用例）。
 */
@DisplayName("RuleBasedTaskDecomposer Tests")
class RuleBasedTaskDecomposerTest {

    private final RuleBasedTaskDecomposer decomposer = new RuleBasedTaskDecomposer();

    @Test
    @DisplayName("should split two independent requests joined by marker")
    void shouldSplitByExplicitMarker() {
        List<SubTask> result = decomposer.decompose("我年假剩几天，另外社保基数多少");

        assertEquals(2, result.size());
        assertEquals("我年假剩几天", result.get(0).getQuery());
        assertEquals("社保基数多少", result.get(1).getQuery());
    }

    @Test
    @DisplayName("should split multiple question marks")
    void shouldSplitByQuestionMarks() {
        List<SubTask> result = decomposer.decompose("团队有多少人？离职率是多少？");

        assertEquals(2, result.size());
        assertEquals("团队有多少人", result.get(0).getQuery());
        assertEquals("离职率是多少", result.get(1).getQuery());
    }

    @Test
    @DisplayName("should split enumeration sharing a predicate marked by 分别")
    void shouldSplitFenbieEnumeration() {
        List<SubTask> result = decomposer.decompose("年假、病假分别几天");

        assertEquals(2, result.size());
        assertEquals("年假几天", result.get(0).getQuery());
        assertEquals("病假几天", result.get(1).getQuery());
    }

    @Test
    @DisplayName("should split two independent action verbs")
    void shouldSplitIndependentVerbs() {
        List<SubTask> result = decomposer.decompose("查看员工绩效，同时看看考勤情况");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("should not split when 还有 means remaining")
    void shouldNotSplitRemainingWord() {
        assertTrue(decomposer.decompose("我年假还有几天").isEmpty());
    }

    @Test
    @DisplayName("should not split shared predicate with parallel objects")
    void shouldNotSplitSharedPredicate() {
        assertTrue(decomposer.decompose("年假以及病假的区别").isEmpty());
        assertTrue(decomposer.decompose("查一下张三的绩效和考勤").isEmpty());
    }

    @Test
    @DisplayName("should not split greeting plus single intent")
    void shouldNotSplitGreeting() {
        assertTrue(decomposer.decompose("你好，我想问下年假").isEmpty());
    }

    @Test
    @DisplayName("should respect max sub task count")
    void shouldRespectMaxSubTasks() {
        List<SubTask> result = decomposer.decompose("查看年假？查看病假？查看考勤？查看绩效？查看编制？查看司龄？");
        assertTrue(result.size() <= 5, "子任务数不得超过上限");
    }

    @Test
    @DisplayName("should return empty for blank query")
    void shouldReturnEmptyForBlank() {
        assertTrue(decomposer.decompose(null).isEmpty());
        assertTrue(decomposer.decompose("   ").isEmpty());
    }

    @Test
    @DisplayName("isComposite should align with decompose")
    void isCompositeShouldAlignWithDecompose() {
        assertTrue(decomposer.isComposite("团队有多少人？离职率是多少？"));
        assertFalse(decomposer.isComposite("我年假还有几天"));
    }
}
