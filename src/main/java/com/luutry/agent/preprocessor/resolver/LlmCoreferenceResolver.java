package com.luutry.agent.preprocessor.resolver;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.common.util.JsonUtils;
import com.luutry.agent.llm.LlmService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LLM 的指代消解器（V2 主力消解引擎）
 * <p>
 * 用途：处理规则快速路径无法覆盖的模糊代词（他/她/它/这个/这些等），
 * 通过结构化 prompt 让 LLM 识别代词并匹配历史中的真实实体，
 * 再经多层校验（JSON结构 → 实体存在性 → 置信度阈值 → 文本完整性）防止幻觉。
 * </p>
 * <p>
 * 核心原则：宁可不错，不可错改。任何校验失败均降级返回原文。
 * </p>
 */
@Slf4j
public class LlmCoreferenceResolver {

    /** LLM 系统提示词：定义角色、任务约束和输出格式 */
    private static final String SYSTEM_PROMPT = """
            你是企业HR管理系统的指代消解引擎。你的任务是识别用户当前查询中的代词，并从对话历史中找到代词所指的真实实体。
            
            严格规则：
            1. 只替换明确的代词（他/她/它/他们/她们/这个/这些/那位/那些等）
            2. 消解实体必须来自对话历史中明确提及的内容，禁止编造
            3. 如果无法确定指代，保持原文不变
            4. 不要修改非代词部分
            5. 严格返回JSON格式，不要添加其他文字
            
            返回格式（严格JSON）：
            {"resolved":true/false,"resolvedQuery":"消解后的查询","pronouns":[{"pronoun":"代词","entity":"指代实体","confidence":0.0-1.0}]}
            """;

    /** 用于从 LLM 响应中提取 JSON 块的正则 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}");

    /** 代词字符：计算内容覆盖率时跳过含这些字符的 2-gram */
    private static final String PRONOUN_CHARS = "他她它这那些该其";

    /** 内容 2-gram 覆盖率下限：低于此值视为消解把原意改掉了 */
    private static final double MIN_CONTENT_COVERAGE = 0.7;

    /** 虚拟线程执行器，用于 LLM 超时控制（JDK 21） */
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final LlmService llmService;
    private final ResolvePolicy policy;

    public LlmCoreferenceResolver(LlmService llmService, ResolvePolicy policy) {
        this.llmService = Objects.requireNonNull(llmService, "llmService must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 执行 LLM 指代消解，包含超时控制和多层校验。
     * 任何异常均降级返回原文。
     * <p>
     * TODO(doc/TODO/018_指代消解缓存与熔断.md): 每次含代词查询都会真实调用一次 LLM，
     * 无会话级缓存、无失败熔断，需补齐后再承接高 QPS。
     * </p>
     *
     * @param query        当前用户查询（已清洗）
     * @param historyTurns 对话历史（最新在前）
     * @return 消解结果，不会为 null
     */
    public ResolveResult resolve(String query, List<String> historyTurns) {
        Future<ResolveResult> future = null;
        try {
            // 超时控制：虚拟线程 + Future.get(timeout）
            future = VIRTUAL_EXECUTOR.submit(() -> doResolve(query, historyTurns));
            return future.get(policy.getLlmTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 放弃等待后必须取消任务，否则虚拟线程里的请求继续跑，白烧一次 token
            if (future != null) {
                future.cancel(true);
            }
            log.warn("LLM指代消解超时({}ms)，降级返回原文: query={}", policy.getLlmTimeoutMs(), query);
            return ResolveResult.unresolved(query);
        } catch (Exception e) {
            log.warn("LLM指代消解异常，降级返回原文: query={}", query, e);
            return ResolveResult.unresolved(query);
        } catch (Throwable t) {
            // 铁律#1：Throwable 兜底，绝不能把业务搞挂
            log.error("LLM指代消解未知错误，降级返回原文: query={}", query, t);
            return ResolveResult.unresolved(query);
        }
    }

    /**
     * 实际 LLM 消解逻辑（在虚拟线程中执行）
     */
    private ResolveResult doResolve(String query, List<String> historyTurns) {
        // 构建 prompt
        String historyText = buildHistoryText(historyTurns);
        String userPrompt = buildUserPrompt(historyText, query);

        // 调用 LLM
        String llmResponse;
        try {
            llmResponse = llmService.generate(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.warn("LLM调用失败，降级返回原文: {}", e.getMessage());
            return ResolveResult.unresolved(query);
        }

        // 校验层1：JSON 结构校验
        JSONObject parsed = parseLlmResponse(llmResponse);
        if (parsed == null) {
            log.warn("LLM响应JSON解析失败，降级返回原文: response={}", truncate(llmResponse, 200));
            return ResolveResult.unresolved(query);
        }

        // 校验层2：resolved 字段检查
        boolean llmResolved = parsed.getBooleanValue("resolved");
        if (!llmResolved) {
            // LLM 自身判断无法消解，信任其判断
            return ResolveResult.unresolved(query);
        }

        String resolvedQuery = parsed.getString("resolvedQuery");
        if (resolvedQuery == null || resolvedQuery.isBlank()) {
            log.warn("LLM返回空resolvedQuery，降级返回原文");
            return ResolveResult.unresolved(query);
        }

        // 校验层3：提取代词-实体映射并校验置信度
        Map<String, String> pronounEntityMap = new LinkedHashMap<>();
        JSONArray pronouns = parsed.getJSONArray("pronouns");
        if (pronouns == null || pronouns.isEmpty()) {
            log.warn("LLM返回空pronouns数组，降级返回原文");
            return ResolveResult.unresolved(query);
        }

        double minConfidence = 1.0;
        String historyJoined = normalizeForMatch(String.join(" ", historyTurns));

        for (int i = 0; i < pronouns.size(); i++) {
            JSONObject item = pronouns.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String pronoun = item.getString("pronoun");
            String entity = item.getString("entity");
            double confidence = item.containsKey("confidence") ? item.getDoubleValue("confidence") : 0.0;

            if (pronoun == null || entity == null || pronoun.isBlank() || entity.isBlank()) {
                continue;
            }

            // 校验层4：实体存在性校验 — 消解实体必须出现在历史中（忽略空白差异）
            if (!historyJoined.contains(normalizeForMatch(entity))) {
                log.warn("LLM消解实体[{}]未在历史中找到，降级返回原文", entity);
                return ResolveResult.unresolved(query);
            }

            // 校验层5：置信度阈值校验
            if (confidence < policy.getMinLlmConfidence()) {
                log.warn("LLM消解置信度[{}/{}]低于阈值[{}]，降级返回原文",
                        String.format("%.2f", confidence), pronoun,
                        policy.getMinLlmConfidence());
                return ResolveResult.unresolved(query);
            }

            pronounEntityMap.put(pronoun, entity);
            minConfidence = Math.min(minConfidence, confidence);
        }

        if (pronounEntityMap.isEmpty()) {
            log.warn("无有效代词映射，降级返回原文");
            return ResolveResult.unresolved(query);
        }

        // 校验层6：文本完整性校验 — 消解后文本不应丢失原文关键信息
        if (!validateTextIntegrity(query, resolvedQuery)) {
            log.warn("消解后文本完整性校验失败，降级返回原文: original={}, resolved={}", query, resolvedQuery);
            return ResolveResult.unresolved(query);
        }

        // 所有校验通过，构建结果
        return ResolveResult.llmResult(resolvedQuery, minConfidence, pronounEntityMap);
    }

    /**
     * 构建历史文本（截断控制 token 消耗）
     */
    private String buildHistoryText(List<String> historyTurns) {
        if (historyTurns == null || historyTurns.isEmpty()) {
            return "（无对话历史）";
        }

        // 从最新到最旧，累积字符直到超过限制
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        int turnCount = 0;

        for (String turn : historyTurns) {
            if (turn == null || turn.isBlank()) {
                continue;
            }
            if (totalChars + turn.length() > policy.getMaxHistoryChars()) {
                break;
            }
            if (turnCount > 0) {
                sb.append("\n");
            }
            sb.append(turn);
            totalChars += turn.length();
            turnCount++;
        }

        return turnCount > 0 ? sb.toString() : "（无有效历史）";
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String history, String query) {
        return """
                对话历史：
                %s
                
                当前查询：%s
                
                请识别当前查询中的代词并从历史中找到指代实体。
                """.formatted(history, query);
    }

    /**
     * 解析 LLM 响应中的 JSON。
     * 支持直接 JSON 和被 markdown/文字包裹的 JSON。
     */
    private JSONObject parseLlmResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        // 尝试直接解析
        String trimmed = response.trim();
        JSONObject direct = JsonUtils.fromJson(trimmed, JSONObject.class);
        if (direct != null) {
            return direct;
        }

        // 尝试提取 JSON 块（LLM 可能在 JSON 外包裹 markdown 代码块）
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return JsonUtils.fromJson(matcher.group(), JSONObject.class);
        }

        return null;
    }

    /**
     * 文本完整性校验：确保消解后文本保留了原文的核心语义。
     * <p>
     * 两道检查：长度不偏离 50%~300%；原文"非代词内容"的汉字 2-gram 覆盖率 ≥ 70%。
     * 只查长度挡不住"那他和她的绩效对比"→"张三的绩效"这类把问题后半句直接吞掉的改写。
     * </p>
     */
    private boolean validateTextIntegrity(String original, String resolved) {
        if (original == null || resolved == null) {
            return false;
        }
        int origLen = original.length();
        int resLen = resolved.length();

        if (resLen < origLen * 0.5 || resLen > origLen * 3.0) {
            return false;
        }

        return contentCoverage(original, resolved) >= MIN_CONTENT_COVERAGE;
    }

    /**
     * 计算原文非代词内容被消解结果保留的比例（按汉字 2-gram）。
     * 含代词字符的 2-gram 不计入，因为它们的消失是消解的正常结果。
     */
    private double contentCoverage(String original, String resolved) {
        int total = 0;
        int kept = 0;
        for (int i = 0; i + 2 <= original.length(); i++) {
            char first = original.charAt(i);
            char second = original.charAt(i + 1);
            if (!isHan(first) || !isHan(second)) {
                continue;
            }
            if (PRONOUN_CHARS.indexOf(first) >= 0 || PRONOUN_CHARS.indexOf(second) >= 0) {
                continue;
            }
            total++;
            if (resolved.contains(original.substring(i, i + 2))) {
                kept++;
            }
        }
        return total == 0 ? 1.0 : (double) kept / total;
    }

    /** 去掉空白后再比对，避免历史跨行/实体带空格导致存在性校验误判 */
    private String normalizeForMatch(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private boolean isHan(char c) {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    /**
     * 截断字符串用于日志输出
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
