package com.luutry.agent.security.injection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt注入检测器
 * <p>
 * 用途：检测用户输入中是否包含Prompt注入攻击模式，防止恶意用户通过构造特殊输入
 * 绕过Agent安全约束（如让Agent忽略系统指令、泄露内部配置等）。
 * 处理问题：LLM Agent面临Prompt注入风险，攻击者可能通过"忽略之前指令"、
 * "你现在是..."等方式劫持Agent行为，导致数据泄露或执行非预期操作。
 * 处理方式：
 * 1. 维护一组已知注入模式的正则规则（中英文双语覆盖）；
 * 2. 逐一扫描用户输入，命中规则则累积风险分数；
 * 3. 返回检测结果（是否可疑+风险分数+命中规则列表）；
 * 4. Planner可据此决定是否拒绝该请求。
 * </p>
 */
// TODO(doc/TODO/016_注入检测未接入预处理管道.md): 检测器已实现但全仓库无调用点，
// 需在 QueryRouter 清洗后、拆分前接入，命中阈值时中止并说明原因
@Component
public class PromptInjectionDetector {

    /**
     * 风险分数阈值，超过此值输入被视为可疑
     */
    private static final int RISK_THRESHOLD = 50;

    private static final List<InjectionRule> RULES = List.of(
            new InjectionRule("ignore_instructions",
                    Pattern.compile("(?i)(ignore|forget|disregard|override|bypass)\\s+(all\\s+)?(previous|prior|above|system)\\s+(instructions|prompts|rules)"),
                    80),
            new InjectionRule("role_hijack",
                    Pattern.compile("(?i)(you\\s+are\\s+now|act\\s+as|pretend\\s+to\\s+be|become|switch\\s+to)\\s+(a\\s+)?"),
                    70),
            new InjectionRule("system_prompt_leak",
                    Pattern.compile("(?i)(show|reveal|display|output|print|repeat)\\s+(me\\s+)?(your|the|my)\\s+(system\\s+)?(prompt|instructions|rules|configuration)"),
                    90),
            new InjectionRule("chinese_ignore",
                    Pattern.compile("忽略(之前|以上|所有|前面)?(的)?(所有|之前|以上|前面)?(的)?(指令|规则|提示|限制|约束)"),
                    80),
            new InjectionRule("chinese_role_hijack",
                    Pattern.compile("(你现在是|请你扮演|假装你是|切换为|变成)(一个)?"),
                    70),
            new InjectionRule("chinese_leak",
                    Pattern.compile("(展示|告诉我|输出|泄露|重复)(你的|系统|我的)?(你的|系统|我的)?(提示词|指令|规则|配置|prompt)"),
                    90),
            new InjectionRule("dan_attack",
                    Pattern.compile("(?i)(DAN|do\\s+anything\\s+now|jailbreak)"),
                    85),
            new InjectionRule("encoding_bypass",
                    Pattern.compile("(?i)(base64|rot13|hex\\s+encode|unicode\\s+escape)\\s+(decode|encode)"),
                    60)
    );

    /**
     * 检测用户输入中的Prompt注入攻击
     *
     * @param userInput 原始用户输入文本
     * @return 包含风险分数和命中规则的检测结果
     */
    public DetectionResult detect(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return DetectionResult.safe();
        }

        int totalRiskScore = 0;
        List<String> matchedRules = new java.util.ArrayList<>();

        for (InjectionRule rule : RULES) {
            if (rule.pattern().matcher(userInput).find()) {
                matchedRules.add(rule.name());
                totalRiskScore += rule.riskScore();
            }
        }

        boolean suspicious = totalRiskScore >= RISK_THRESHOLD;
        return new DetectionResult(suspicious, Math.min(totalRiskScore, 100), matchedRules);
    }

    /**
     * 检查用户输入是否安全（无可疑内容）
     *
     * @param userInput 原始用户输入文本
     * @return 输入安全时返回true
     */
    public boolean isSafe(String userInput) {
        return !detect(userInput).isSuspicious();
    }

    /**
     * 内部注入规则定义
     */
    private record InjectionRule(String name, Pattern pattern, int riskScore) {
    }

    /**
     * 检测结果，包含风险评估信息
     */
    @Getter
    @AllArgsConstructor
    public static class DetectionResult {
        private final boolean suspicious;
        private final int riskScore;
        private final List<String> matchedRules;

        public static DetectionResult safe() {
            return new DetectionResult(false, 0, List.of());
        }
    }
}
