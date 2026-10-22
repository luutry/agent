package com.luutry.agent.preprocessor.cleaner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * QueryInputDesensitizer 单元测试。
 * 覆盖：连续号码、分隔符号码、国家码前缀、普通数字不误伤、fail-closed 兜底。
 */
@DisplayName("QueryInputDesensitizer 输入脱敏测试")
class QueryInputDesensitizerTest {

    private final QueryInputDesensitizer desensitizer = new QueryInputDesensitizer();

    @Test
    @DisplayName("连续手机号被脱敏")
    void desensitize_contiguousPhone_masked() {
        assertEquals("联系 138****5678", desensitizer.desensitize("联系 13812345678"));
    }

    @Test
    @DisplayName("空格分隔的手机号被脱敏")
    void desensitize_spaceSeparatedPhone_masked() {
        assertEquals("联系 138****5678", desensitizer.desensitize("联系 138 1234 5678"));
    }

    @Test
    @DisplayName("短横线分隔的手机号被脱敏")
    void desensitize_hyphenSeparatedPhone_masked() {
        assertEquals("联系 138****5678", desensitizer.desensitize("联系 138-1234-5678"));
    }

    @Test
    @DisplayName("+86 前缀手机号被脱敏")
    void desensitize_countryCodePhone_masked() {
        assertEquals("联系 138****5678", desensitizer.desensitize("联系 +86 138 1234 5678"));
    }

    @Test
    @DisplayName("空格分隔的身份证号被脱敏")
    void desensitize_separatedIdCard_masked() {
        assertEquals("110***********1234", desensitizer.desensitize("110101 19900307 1234"));
    }

    @Test
    @DisplayName("日期等普通数字不误脱敏")
    void desensitize_plainNumbers_untouched() {
        assertEquals("2026-10-22 入职", desensitizer.desensitize("2026-10-22 入职"));
    }

    @Test
    @DisplayName("工号不被当成号码")
    void desensitize_employeeId_untouched() {
        assertEquals("查 E1001 的绩效", desensitizer.desensitize("查 E1001 的绩效"));
    }

    @Test
    @DisplayName("fail-closed 兜底抹除所有数字，不依赖正则")
    void failClosedMask_digitsReplaced() {
        assertEquals("**-**", QueryInputDesensitizer.failClosedMask("12-34"));
        assertFalse(QueryInputDesensitizer.failClosedMask("13812345678").contains("1"));
    }
}
