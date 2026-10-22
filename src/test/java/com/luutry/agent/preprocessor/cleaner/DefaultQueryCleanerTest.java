package com.luutry.agent.preprocessor.cleaner;

import com.luutry.agent.common.constant.AgentConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultQueryCleaner 单元测试。
 * 覆盖：正向、边界、异常降级、安全（ReDoS/代理对/控制字符）、
 * CleanResult 元信息、语义边界截断、HARD 拒答标记、PII 脱敏顺序。
 */
@DisplayName("DefaultQueryCleaner 查询清洗测试")
class DefaultQueryCleanerTest {

    private DefaultQueryCleaner cleaner;
    private CleanPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CleanPolicy();
        // 使用默认值：warn=2000, hardReject=2000, multiplier=4, semanticWindow=50
        cleaner = new DefaultQueryCleaner(policy, new QueryInputDesensitizer());
    }

    // ==================== 基础功能测试 ====================

    @Nested
    @DisplayName("基础清洗功能")
    class BasicCleanTests {

        @Test
        @DisplayName("正常中文输入保持不变，CleanResult 标记未截断")
        void clean_normalInput_returnsAsIs() {
            CleanResult result = cleaner.clean("查看团队绩效");
            assertEquals("查看团队绩效", result.getCleanedText());
            assertFalse(result.isTruncated());
            assertFalse(result.isHardRejected());
            assertFalse(result.isFilteredToEmpty());
            assertEquals(TruncationBoundary.NONE, result.getBoundary());
        }

        @Test
        @DisplayName("null 输入返回空 CleanResult，filteredToEmpty=true")
        void clean_nullInput_returnsEmpty() {
            CleanResult result = cleaner.clean(null);
            assertEquals("", result.getCleanedText());
            assertTrue(result.isFilteredToEmpty());
            assertFalse(result.isTruncated());
            assertEquals(TruncationBoundary.NONE, result.getBoundary());
        }

        @Test
        @DisplayName("空白输入返回空 CleanResult，filteredToEmpty=true")
        void clean_blankInput_returnsEmpty() {
            CleanResult result = cleaner.clean("   ");
            assertEquals("", result.getCleanedText());
            assertTrue(result.isFilteredToEmpty());
        }

        @Test
        @DisplayName("混合空白和噪声正确清洗")
        void clean_mixedNoise_cleaned() {
            CleanResult result = cleaner.clean("  \t 查看  \n 团队  绩效  \t  ");
            assertEquals("查看 团队 绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("C0 控制字符被移除")
        void clean_controlChars_removed() {
            CleanResult result = cleaner.clean("查看\u0000团队\u0001绩效\u0002");
            assertEquals("查看团队绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("多个空白字符归一化为单个空格")
        void clean_multipleWhitespace_normalized() {
            CleanResult result = cleaner.clean("查看   团队\t绩效\n");
            assertEquals("查看 团队 绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("连续重复全角标点合并为 1 个，尾部标点保留")
        void clean_repeatedPunctuation_collapsed() {
            CleanResult result = cleaner.clean("查看团队绩效？？？");
            assertEquals("查看团队绩效？", result.getCleanedText());
        }

        @Test
        @DisplayName("中间重复全角标点合并后保留")
        void clean_repeatedPunctuationInMiddle_collapsed() {
            CleanResult result = cleaner.clean("查看？？？团队绩效");
            assertEquals("查看？团队绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("首部重复全角标点合并后被移除")
        void clean_repeatedExclamation_collapsed() {
            CleanResult result = cleaner.clean("！！！紧急查询");
            assertEquals("紧急查询", result.getCleanedText());
        }

        @Test
        @DisplayName("首部标点噪声被移除")
        void clean_leadingPunctuation_removed() {
            CleanResult result = cleaner.clean("，，查看团队");
            assertEquals("查看团队", result.getCleanedText());
        }
    }

    // ==================== CleanResult 元信息测试 ====================

    @Nested
    @DisplayName("CleanResult 元信息")
    class CleanResultMetadataTests {

        @Test
        @DisplayName("originalCodePoints 正确记录原始 code point 数量")
        void clean_recordsOriginalCodePoints() {
            CleanResult result = cleaner.clean("查看团队绩效");
            assertEquals(6, result.getOriginalCodePoints());
        }

        @Test
        @DisplayName("originalHash 不为 0")
        void clean_recordsOriginalHash() {
            CleanResult result = cleaner.clean("查看团队绩效");
            assertNotEquals(0, result.getOriginalHash());
        }

        @Test
        @DisplayName("isUsable() 正常输入返回 true")
        void clean_normalInput_isUsable() {
            CleanResult result = cleaner.clean("查看团队绩效");
            assertTrue(result.isUsable());
        }

        @Test
        @DisplayName("isUsable() 空输入返回 false")
        void clean_emptyInput_isNotUsable() {
            CleanResult result = cleaner.clean("   ");
            assertFalse(result.isUsable());
        }
    }

    // ==================== 全角空格与 NBSP 处理 ====================

    @Nested
    @DisplayName("全角空格与 NBSP 处理")
    class FullWidthSpaceTests {

        @Test
        @DisplayName("全角空格 \\u3000 被归一化为普通空格")
        void clean_fullWidthSpace_normalized() {
            CleanResult result = cleaner.clean("查看\u3000团队绩效");
            assertEquals("查看 团队绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("NBSP \\u00A0 被归一化为普通空格")
        void clean_nbsp_normalized() {
            CleanResult result = cleaner.clean("查看\u00A0团队绩效");
            assertEquals("查看 团队绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("连续全角空格归一化为单个空格")
        void clean_multipleFullWidthSpace_normalized() {
            CleanResult result = cleaner.clean("查看\u3000\u3000\u3000团队");
            assertEquals("查看 团队", result.getCleanedText());
        }
    }

    // ==================== 半角标点处理 ====================

    @Nested
    @DisplayName("半角标点处理")
    class HalfWidthPunctTests {

        @Test
        @DisplayName("半角 ??? 合并为 ?")
        void clean_halfWidthQuestionMarks_collapsed() {
            CleanResult result = cleaner.clean("查看???绩效");
            assertEquals("查看?绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("半角 !!! 合并为 !")
        void clean_halfWidthExclamation_collapsed() {
            CleanResult result = cleaner.clean("!!!紧急");
            assertEquals("紧急", result.getCleanedText());
        }

        @Test
        @DisplayName("半角 ... 合并为 .")
        void clean_halfWidthDot_collapsed() {
            CleanResult result = cleaner.clean("思考...");
            assertEquals("思考.", result.getCleanedText());
        }
    }

    // ==================== 控制字符与安全测试 ====================

    @Nested
    @DisplayName("控制字符与安全")
    class ControlCharSecurityTests {

        @Test
        @DisplayName("C1 控制符 \\u0080-\\u009F 被移除")
        void clean_c1ControlChars_removed() {
            CleanResult result = cleaner.clean("查看\u0080团队\u009F绩效");
            assertEquals("查看团队绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("行分隔符 \\u2028 被移除")
        void clean_lineSeparator_removed() {
            CleanResult result = cleaner.clean("查看\u2028团队");
            assertEquals("查看团队", result.getCleanedText());
        }

        @Test
        @DisplayName("段分隔符 \\u2029 被移除")
        void clean_paragraphSeparator_removed() {
            CleanResult result = cleaner.clean("查看\u2029团队");
            assertEquals("查看团队", result.getCleanedText());
        }

        @Test
        @DisplayName("双向控制符 \\u202E (RTL override) 被移除")
        void clean_bidiControl_removed() {
            CleanResult result = cleaner.clean("查看\u202E团队");
            assertEquals("查看团队", result.getCleanedText());
        }

        @Test
        @DisplayName("隔离区双向控制符 \\u2066 被移除")
        void clean_isolateBidiControl_removed() {
            CleanResult result = cleaner.clean("查看\u2066团队");
            assertEquals("查看团队", result.getCleanedText());
        }

        @Test
        @DisplayName("ZWJ(\\u200D) 被移除：查询不需要显示级连字，保留会被用来拆断关键词")
        void clean_zwj_removed() {
            String input = "团队\uD83D\uDC68\u200D\uD83D\uDC69成员";
            CleanResult result = cleaner.clean(input);
            assertFalse(result.getCleanedText().contains("\u200D"), "ZWJ 应被移除");
        }

        @Test
        @DisplayName("ZWNJ(\\u200C) 被移除：否则可插进数字串绕过 PII 正则")
        void clean_zwnj_removed() {
            String input = "فارسی\u200Cزبان";
            CleanResult result = cleaner.clean(input);
            assertFalse(result.getCleanedText().contains("\u200C"), "ZWNJ 应被移除");
        }

        @Test
        @DisplayName("软连字符 \\u00AD 被移除，不再拆断关键词")
        void clean_softHyphen_removed() {
            CleanResult result = cleaner.clean("绩\u00AD效");
            assertEquals("绩效", result.getCleanedText());
        }

        @Test
        @DisplayName("word joiner \\u2060 被移除")
        void clean_wordJoiner_removed() {
            CleanResult result = cleaner.clean("团\u2060队");
            assertEquals("团队", result.getCleanedText());
        }

        @Test
        @DisplayName("BOM(\\uFEFF) 被移除")
        void clean_bom_removed() {
            CleanResult result = cleaner.clean("\uFEFF查看团队");
            assertEquals("查看团队", result.getCleanedText());
        }
    }

    // ==================== 归一化与脱敏加固 ====================

    @Nested
    @DisplayName("归一化与脱敏加固")
    class NormalizationAndPiiTests {

        @Test
        @DisplayName("全角手机号归一化后被脱敏")
        void clean_fullWidthPhone_masked() {
            CleanResult result = cleaner.clean("查一下１３８１２３４５６７８的机主");
            assertTrue(result.getCleanedText().contains("138****5678"),
                    "实际=" + result.getCleanedText());
            assertFalse(result.getCleanedText().contains("１"), "全角数字应被归一化");
        }

        @Test
        @DisplayName("全角工号归一化为半角，供下游取参")
        void clean_fullWidthEmployeeId_normalized() {
            CleanResult result = cleaner.clean("查Ｅ１００１的绩效");
            assertTrue(result.getCleanedText().contains("E1001"),
                    "实际=" + result.getCleanedText());
        }

        @Test
        @DisplayName("空格/短横线分隔的手机号被脱敏")
        void clean_separatedPhone_masked() {
            assertEquals("联系 138****5678", cleaner.clean("联系 138 1234 5678").getCleanedText());
            assertEquals("联系 138****5678", cleaner.clean("联系 138-1234-5678").getCleanedText());
        }

        @Test
        @DisplayName("+86 前缀手机号被脱敏")
        void clean_phoneWithCountryCode_masked() {
            assertEquals("联系 138****5678", cleaner.clean("联系 +86 138 1234 5678").getCleanedText());
        }

        @Test
        @DisplayName("日期等普通数字不被误脱敏")
        void clean_plainNumbers_notMasked() {
            assertTrue(cleaner.clean("2026-10-22 入职的员工").getCleanedText().contains("2026-10-22"));
        }

        @Test
        @DisplayName("词边界截断贴近阈值，不再白丢整个搜索窗口")
        void clean_wordBoundary_keepsMostText() {
            String input = "查询 ".repeat(700);
            CleanResult result = cleaner.clean(input);
            assertTrue(result.isTruncated());
            assertEquals(TruncationBoundary.WORD, result.getBoundary());
            assertTrue(result.getCleanedText().length() >= policy.getWarnThresholdChars() - 2,
                    "实际长度=" + result.getCleanedText().length());
        }

        @Test
        @DisplayName("超过硬上限时 hardLimited=true")
        void clean_exceedsHardLimit_hardLimited() {
            int hardLimit = policy.getWarnThresholdChars() * policy.getHardLimitMultiplier();
            CleanResult result = cleaner.clean("A".repeat(hardLimit + 10));
            assertTrue(result.isHardLimited());
            assertTrue(result.isTruncated());
        }
    }

    // ==================== 截断与边界测试 ====================

    @Nested
    @DisplayName("截断与边界")
    class TruncationBoundaryTests {

        @Test
        @DisplayName("超过 WARN 阈值时 truncated=true，boundary=SENTENCE")
        void clean_exceedsWarnThreshold_truncatedAtSentence() {
            // 构造一个明显超过 1500 字符的文本，包含句子边界
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                sb.append("这是第").append(i + 1).append("句查询内容，请仔细查看。");
            }
            // 每句约 16 字符，300 句 ≈ 4800 字符，远超 WARN 阈值 1500
            assertTrue(sb.length() > policy.getWarnThresholdChars(), "测试文本应超过 WARN 阈值");
            CleanResult result = cleaner.clean(sb.toString());
            assertTrue(result.isTruncated(), "超过 WARN 阈值应标记 truncated");
            assertEquals(TruncationBoundary.SENTENCE, result.getBoundary(),
                    "应在句子边界截断");
            assertTrue(result.getCleanedText().length() <= policy.getWarnThresholdChars(),
                    "截断后文本不应超过 WARN 阈值");
        }

        @Test
        @DisplayName("超过 WARN 阈值但无句子边界时，boundary=WORD")
        void clean_exceedsWarnThresholdNoSentence_truncatedAtWord() {
            // 构造无句子终止符的超长文本（用空格分隔的单词）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                sb.append("keyword").append(i).append(" ");
            }
            CleanResult result = cleaner.clean(sb.toString());
            assertTrue(result.isTruncated());
            // 应该回退到 WORD 边界（因为有空格）
            assertTrue(result.getBoundary() == TruncationBoundary.WORD
                            || result.getBoundary() == TruncationBoundary.CODE_POINT,
                    "无句子边界时应回退到 WORD 或 CODE_POINT");
        }

        @Test
        @DisplayName("超过 WARN 阈值且无任何边界时，boundary=CODE_POINT")
        void clean_exceedsWarnThresholdNoBoundary_codePointTruncation() {
            // 构造无空格无标点的超长连续文本
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3000; i++) {
                sb.append("A");
            }
            CleanResult result = cleaner.clean(sb.toString());
            assertTrue(result.isTruncated());
            assertEquals(TruncationBoundary.CODE_POINT, result.getBoundary());
            assertTrue(result.getCleanedText().length() <= policy.getWarnThresholdChars());
        }

        @Test
        @DisplayName("超过 HARD 阈值时 hardRejected=true")
        void clean_exceedsHardThreshold_hardRejected() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3000; i++) {
                sb.append("A");
            }
            CleanResult result = cleaner.clean(sb.toString());
            assertTrue(result.isHardRejected(), "超过 HARD 阈值应标记 hardRejected");
        }

        @Test
        @DisplayName("未超过 WARN 阈值时 truncated=false")
        void clean_withinWarnThreshold_notTruncated() {
            CleanResult result = cleaner.clean("查看团队绩效");
            assertFalse(result.isTruncated());
            assertEquals(TruncationBoundary.NONE, result.getBoundary());
        }

        @Test
        @DisplayName("截断不切断 surrogate pair（emoji 安全）")
        void clean_truncateAtSurrogatePair_noBrokenEmoji() {
            // 构造刚好在 WARN 阈值处有 emoji 的文本
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < policy.getWarnThresholdChars() - 1; i++) {
                sb.append("A");
            }
            sb.append("\uD83D\uDE00"); // emoji (surrogate pair)
            CleanResult result = cleaner.clean(sb.toString());
            String text = result.getCleanedText();
            // 验证没有孤立代理对
            for (int i = 0; i < text.length(); i++) {
                if (Character.isHighSurrogate(text.charAt(i))) {
                    assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)),
                            "不应产生孤立 high surrogate at index " + i);
                }
            }
        }
    }

    // ==================== ReDoS 防护测试 ====================

    @Nested
    @DisplayName("ReDoS 防护")
    class HardLimitTests {

        @Test
        @DisplayName("超过硬截断上限的输入被截断后再清洗")
        void clean_exceedsHardLimit_truncatedFirst() {
            int hardLimit = policy.getWarnThresholdChars() * policy.getHardLimitMultiplier();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hardLimit + 5000; i++) {
                sb.append("A");
            }
            CleanResult result = cleaner.clean(sb.toString());
            // 最终结果不超过 WARN 阈值
            assertTrue(result.getCleanedText().length() <= policy.getWarnThresholdChars());
            assertTrue(result.isTruncated());
            assertTrue(result.isHardRejected());
        }

        @Test
        @DisplayName("硬截断后的输入仍可正常清洗")
        void clean_hardLimitInput_cleanedCorrectly() {
            int hardLimit = policy.getWarnThresholdChars() * policy.getHardLimitMultiplier();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hardLimit; i++) {
                sb.append("A");
            }
            sb.append("？？？   ");
            CleanResult result = cleaner.clean(sb.toString());
            assertTrue(result.getCleanedText().length() <= policy.getWarnThresholdChars());
        }
    }

    // ==================== PII 脱敏测试 ====================

    @Nested
    @DisplayName("PII 脱敏（截断前执行）")
    class DesensitizationTests {

        @Test
        @DisplayName("手机号在清洗后被脱敏")
        void clean_phoneNumber_desensitized() {
            CleanResult result = cleaner.clean("查询员工13812341234的绩效");
            assertTrue(result.getCleanedText().contains("138****1234"));
            assertFalse(result.getCleanedText().contains("13812341234"));
        }

        @Test
        @DisplayName("邮箱在清洗后被脱敏")
        void clean_email_desensitized() {
            CleanResult result = cleaner.clean("发送通知到luutry@gmail.com");
            assertTrue(result.getCleanedText().contains("l***@gmail.com"));
        }

        @Test
        @DisplayName("身份证号在清洗后被脱敏")
        void clean_idCard_desensitized() {
            CleanResult result = cleaner.clean("员工身份证110101199001011234信息");
            assertTrue(result.getCleanedText().contains("110"));
            assertFalse(result.getCleanedText().contains("110101199001011234"));
        }
    }

    // ==================== 异常降级测试 ====================

    @Nested
    @DisplayName("异常降级")
    class DegradationTests {

        @Test
        @DisplayName("CleanResult.ok() 创建正常结果")
        void cleanResult_ok_createsNormalResult() {
            CleanResult result = CleanResult.ok("正常文本");
            assertEquals("正常文本", result.getCleanedText());
            assertFalse(result.isTruncated());
            assertFalse(result.isHardRejected());
            assertFalse(result.isFilteredToEmpty());
            assertEquals(TruncationBoundary.NONE, result.getBoundary());
            assertTrue(result.isUsable());
        }

        @Test
        @DisplayName("CleanResult.degraded() 创建降级结果")
        void cleanResult_degraded_createsDegradedResult() {
            CleanResult result = CleanResult.degraded("降级文本", 100);
            assertEquals("降级文本", result.getCleanedText());
            assertTrue(result.isTruncated());
            assertEquals(TruncationBoundary.HARD_LIMIT, result.getBoundary());
            assertEquals(100, result.getOriginalCodePoints());
        }

        @Test
        @DisplayName("CleanResult.ok() 空文本标记 filteredToEmpty")
        void cleanResult_ok_emptyText_filteredToEmpty() {
            CleanResult result = CleanResult.ok("");
            assertTrue(result.isFilteredToEmpty());
            assertFalse(result.isUsable());
        }
    }

    // ==================== 配置驱动测试 ====================

    @Nested
    @DisplayName("CleanPolicy 配置驱动")
    class CleanPolicyTests {

        @Test
        @DisplayName("自定义 WARN 阈值生效")
        void clean_customWarnThreshold_takesEffect() {
            CleanPolicy customPolicy = new CleanPolicy();
            customPolicy.setWarnThresholdChars(10);
            customPolicy.setHardRejectThresholdChars(100);
            customPolicy.setHardLimitMultiplier(4);
            customPolicy.setSemanticBoundaryChars(5);
            DefaultQueryCleaner customCleaner = new DefaultQueryCleaner(customPolicy, new QueryInputDesensitizer());

            CleanResult result = customCleaner.clean("这是一段超过十个字符的查询文本内容");
            assertTrue(result.isTruncated(), "超过自定义 WARN 阈值应标记 truncated");
            assertTrue(result.getCleanedText().length() <= 10);
        }

        @Test
        @DisplayName("自定义 HARD 阈值生效")
        void clean_customHardThreshold_takesEffect() {
            CleanPolicy customPolicy = new CleanPolicy();
            customPolicy.setWarnThresholdChars(50);
            customPolicy.setHardRejectThresholdChars(20);
            customPolicy.setHardLimitMultiplier(4);
            customPolicy.setSemanticBoundaryChars(10);
            DefaultQueryCleaner customCleaner = new DefaultQueryCleaner(customPolicy, new QueryInputDesensitizer());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 30; i++) {
                sb.append("A");
            }
            CleanResult result = customCleaner.clean(sb.toString());
            assertTrue(result.isHardRejected(), "超过自定义 HARD 阈值应标记 hardRejected");
        }
    }

    // ==================== 配置校验 ====================

    @Nested
    @DisplayName("CleanPolicy 配置校验")
    class CleanPolicyValidationTests {

        @Test
        @DisplayName("非法阈值被修正为安全默认值")
        void validate_invalidValues_fixed() {
            CleanPolicy invalid = new CleanPolicy();
            invalid.setWarnThresholdChars(0);
            invalid.setHardRejectThresholdChars(-1);
            invalid.setHardLimitMultiplier(0);
            invalid.setSemanticBoundaryChars(0);

            invalid.validate();

            assertEquals(AgentConstants.MAX_QUERY_LENGTH, invalid.getWarnThresholdChars());
            assertTrue(invalid.getHardRejectThresholdChars() >= invalid.getWarnThresholdChars());
            assertEquals(AgentConstants.HARD_LIMIT_MULTIPLIER, invalid.getHardLimitMultiplier());
            assertEquals(50, invalid.getSemanticBoundaryChars());
        }

        @Test
        @DisplayName("HARD 小于 WARN 时抬到 WARN")
        void validate_hardBelowWarn_raised() {
            CleanPolicy invalid = new CleanPolicy();
            invalid.setWarnThresholdChars(100);
            invalid.setHardRejectThresholdChars(10);

            invalid.validate();

            assertEquals(100, invalid.getHardRejectThresholdChars());
        }
    }
}
