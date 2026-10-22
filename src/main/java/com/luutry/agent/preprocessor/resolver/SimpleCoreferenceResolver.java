package com.luutry.agent.preprocessor.resolver;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.observability.trace.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指代消解编排器（V2）
 * <p>
 * 用途：作为指代消解的入口，编排「规则触发器 → 高置信快速路径 → LLM主力消解」三层架构。
 * 核心原则：宁可不错，不可错改。
 * </p>
 * <p>
 * 三层处理流程：
 * 1. Tier 1 规则触发器：检测 query 是否含独立代词（排除"其他/因此/其中"等误触发）
 * 2. Tier 2 高置信快速路径：仅处理"该员工/该部门/刚才那些"等明确模式，从历史提取匹配实体
 * 3. Tier 3 LLM主力消解：委托 {@link LlmCoreferenceResolver} 处理模糊代词，含多层校验
 * </p>
 * <p>
 * 降级策略：任何阶段异常均降级返回原文，标记 resolved=false, method=DEGRADED。
 * </p>
 */
@Slf4j
@Component
public class SimpleCoreferenceResolver implements CoreferenceResolver {

    // ==================== 代词检测相关常量 ====================

    /**
     * 含代词字符的常见非代词复合词列表（按长度降序排列，确保长词优先匹配剥离）。
     * 用途：从文本中先剥离这些词，再检测是否残留独立代词，避免误触发。
     * 例如："其他国家" → 剥离"其他" → 无残留代词 → 不触发消解。
     * 时间表达（这个月/这些天…）必须排在前面，否则会被当作代词触发无意义的 LLM 调用。
     */
    private static final List<String> PRONOUN_COMPOUND_WORDS = List.of(
            "这个月", "这个季度", "这个星期", "这些天", "那些年", "那时候", "那会儿",
            "其它", "其他", "因此", "其中", "其次", "尤其",
            "其实", "其余", "彼此", "此致",
            "此外", "如此", "之一", "之后", "之前"
    );

    /** 独立代词检测正则（长代词优先匹配，避免"他们"被"他"抢先匹配） */
    private static final Pattern PRONOUN_PATTERN = Pattern.compile(
            "他们|她们|它们|那个|那些|那位|这个|这些|这批|刚才那些|刚才的|该员工|该部门|该团队"
                    + "|前者|后者|上述|其(?=[绩考薪年假合福培部工])|他|她|它"
    );

    /** 高置信度快速路径模式（配置化，由 ResolvePolicy 驱动） */
    private static final Map<String, String> PATTERN_TO_CATEGORY = Map.of(
            "该员工", "person",
            "该部门", "department",
            "该团队", "department",
            "刚才那些", "recent_entity",
            "刚才的", "recent_entity"
    );

    /** 人名前缀最大字符数（提取历史中"X+后缀"模式的人名时使用） */
    private static final int MAX_NAME_PREFIX_CHARS = 6;

    // ==================== 省略补全相关常量 ====================

    /** 当前查询视为"极短输入"（纯人名/实体）的最大字符数 */
    private static final int ELLIPSIS_MAX_QUERY_LENGTH = 6;

    /** 从上一轮提取谓语的最小长度，过短说明上一轮也不是完整查询 */
    private static final int ELLIPSIS_MIN_PREDICATE_LENGTH = 2;

    /** 完整问句检测：疑问词/疑问助词 */
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "[吗呢？?]|多少|怎么|什么|哪些|几个|是否|有没有|多少|为何|哪里|哪个|怎样|如何"
    );

    /**
     * 中文人名检测：2~6 个汉字，不含“的/他/她/它/们/了/在/是/看/查/有”等功能字符。
     * 用于省略补全的前置守卫，避免“他的绩效”“其次看考勤”等完整句子被误判为纯人名。
     */
    private static final Pattern CHINESE_NAME_PATTERN = Pattern.compile(
            "^[\\u4e00-\\u9fa5]{2,6}$"
    );

    /** 人名中不应出现的功能字符（代词/助词/动词/介词） */
    private static final String NAME_EXCLUDED_CHARS = "的了他她它们在看是查有问请帮看和与或";

    /** 人名/组织名后缀列表 */
    private static final List<String> ENTITY_SUFFIXES = List.of(
            "部门", "团队", "组", "中心", "事业部",
            "经理", "总监", "主管", "工程师", "分析师"
    );

    /** "XX的绩效/考勤/简历..." 模式：提取 XX 作为人名 */
    private static final Pattern DE_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,6})的(绩效|考勤|简历|档案|离职|入职|调岗|晋升)"
    );

    /** 业务名词：2~6 字时最容易被误判成人名，禁止触发省略补全 */
    private static final Set<String> BUSINESS_NOUNS = Set.of(
            "绩效", "考勤", "年假", "病假", "事假", "请假", "加班", "薪资", "工资", "薪酬",
            "部门", "团队", "制度", "流程", "政策", "社保", "公积金", "离职", "入职", "晋升",
            "调岗", "编制", "人数", "名单", "档案", "简历", "数据", "报表", "指标", "考核",
            "福利", "培训", "合同", "转正", "试用期", "组织", "架构", "审批", "报销", "出差"
    );

    /** 寒暄/应答词：对话里出现时不应被当成新人名 */
    private static final Set<String> GREETING_WORDS = Set.of(
            "你好", "您好", "谢谢", "多谢", "感谢", "好的", "收到", "嗯嗯", "在吗", "再见", "拜拜", "辛苦"
    );

    /** 上一轮谓语必须包含的指标线索，否则继承过来也答不出东西 */
    private static final List<String> METRIC_CUES = List.of(
            "年假", "病假", "事假", "假期", "请假", "加班", "考勤", "绩效", "薪资", "工资", "薪酬",
            "社保", "公积金", "部门", "团队", "人数", "编制", "名单", "离职", "入职", "晋升", "调岗",
            "转正", "合同", "简历", "档案", "制度", "流程", "政策", "福利", "培训", "试用期", "指标", "考核"
    );

    /** 组织后缀：出现在主语里说明上一轮主语不是人名 */
    private static final List<String> ORG_SUFFIXES = List.of("部门", "团队", "组", "中心", "事业部", "公司");

    /** 省略补全允许用作主语的人称代词 */
    private static final Set<String> SUBJECT_PRONOUNS = Set.of(
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们", "它们", "大家"
    );

    /** 实体提取前需要剥掉的前导噪声（动词/虚词/标点），避免把"查一下研发部门"整体当实体 */
    private static final Pattern LEADING_NOISE = Pattern.compile(
            "^(?:麻烦|请|帮我|帮忙|我想|想要|想知道|查询|查看|查一下|看一下|看看|列出|显示|统计|分析|盘点|告诉我|给我|查|看|一下|的|了|在|是|和|与|及)+"
                    + "|^[\\s,，、:：]+"
    );

    private final ResolvePolicy policy;
    private final LlmCoreferenceResolver llmResolver;

    public SimpleCoreferenceResolver(ResolvePolicy policy, LlmCoreferenceResolver llmResolver) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.llmResolver = Objects.requireNonNull(llmResolver, "llmResolver must not be null");
    }

    @Override
    @AgentTrace(stage = "coreference_resolution")
    public ResolveResult resolve(String query, List<String> historyTurns) {
        try {
            return doResolve(query, historyTurns);
        } catch (Throwable t) {
            // 铁律#1：Throwable 兜底，失败降级返回原文
            log.error("指代消解未知异常，降级返回原文: query={}", query, t);
            return ResolveResult.unresolved(query != null ? query : "");
        }
    }

    /**
     * 实际消解编排逻辑
     */
    private ResolveResult doResolve(String query, List<String> historyTurns) {
        // 步骤0：空值/空白快速返回
        if (query == null || query.isBlank()) {
            return ResolveResult.unresolved(query != null ? query : "");
        }

        // 步骤0.5：省略补全 — 当前查询极短（纯人名）时，尝试从上一轮继承谓语
        // 必须在代词检测之前，因为"于娟"不含任何代词，会被步骤1直接跳过
        if (historyTurns != null && !historyTurns.isEmpty()) {
            ResolveResult ellipsisResult = tryEllipsisResolution(query, historyTurns);
            if (ellipsisResult != null) {
                return ellipsisResult;
            }
        }

        // 步骤1：规则触发器 — 检测是否含独立代词
        if (!containsStandalonePronoun(query)) {
            return ResolveResult.unresolved(query);
        }

        // 无历史则无法消解
        if (historyTurns == null || historyTurns.isEmpty()) {
            return ResolveResult.unresolved(query);
        }

        // 步骤2：高置信快速路径
        ResolveResult fastResult = tryRuleFastPath(query, historyTurns);
        if (fastResult != null) {
            return fastResult;
        }

        // 步骤3：LLM主力消解
        return llmResolver.resolve(query, historyTurns);
    }

    // ==================== 省略补全 ====================

    /**
     * 尝试省略补全：当前查询极短（纯人名/实体）且自身不含疑问词时，
     * 从上一轮查询中提取谓语并拼合，生成完整查询。
     * <p>
     * 典型场景：
     * - Turn 1: "我的年假还有多少" → Turn 2: "于娟" → 补全为 "于娟的年假还有多少"
     * - Turn 1: "张三的绩效怎么样" → Turn 2: "李四" → 补全为 "李四的绩效怎么样"
     * </p>
     *
     * @return 省略补全结果，不满足条件时返回 null
     */
    private ResolveResult tryEllipsisResolution(String query, List<String> historyTurns) {
        // 当前查询必须看起来像纯中文人名（2~6 个汉字，无功能字符）
        if (!isLikelyChineseName(query)) {
            return null;
        }
        // 业务名词/寒暄不是人名，补全只会拼出"绩效的年假还有多少"这类病句
        if (isBusinessOrGreeting(query)) {
            return null;
        }
        // 当前查询不能已经是完整问句（含疑问词/助词则不补全）
        if (isCompleteQuestion(query)) {
            return null;
        }

        // 取最近一轮用户查询（historyTurns 最新在前）
        String previousQuery = null;
        for (String turn : historyTurns) {
            if (turn != null && !turn.isBlank()) {
                previousQuery = turn;
                break;
            }
        }
        if (previousQuery == null) {
            return null;
        }

        // 从上一轮中提取谓语部分
        String predicate = extractPredicateFromPrevious(previousQuery);
        if (predicate == null || predicate.length() < ELLIPSIS_MIN_PREDICATE_LENGTH) {
            return null;
        }
        // 上一轮不是指标类问题（如"今天天气怎么样"）时不继承，避免把无关谓语套到人名上
        if (!hasMetricCue(predicate)) {
            return null;
        }

        // 拼合：当前实体 + "的" + 上一轮谓语
        String resolved = query + "的" + predicate;
        log.debug("省略补全: query='{}' + prev='{}' → '{}'", query, previousQuery, resolved);
        return ResolveResult.ellipsisResult(resolved);
    }

    /**
     * 判断文本是否看起来像中文人名（2~6 个纯汉字，不含代词/助词/动词等功能字符）。
     */
    private boolean isLikelyChineseName(String text) {
        if (text == null || text.length() < 2 || text.length() > ELLIPSIS_MAX_QUERY_LENGTH) {
            return false;
        }
        if (!CHINESE_NAME_PATTERN.matcher(text).matches()) {
            return false;
        }
        // 排除含功能字符的文本（如“他的”“看看”等）
        for (char c : NAME_EXCLUDED_CHARS.toCharArray()) {
            if (text.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断文本是否为完整问句（含疑问词/疑问助词）。
     * 完整问句不需要省略补全。
     */
    private boolean isCompleteQuestion(String text) {
        return QUESTION_PATTERN.matcher(text).find();
    }

    /**
     * 从上一轮查询中提取可继承的谓语部分。
     * <p>
     * 只接受"可继承主语 + 的 + 谓语"这一种结构：
     * - "我的年假还有多少" → 谓语 "年假还有多少"
     * - "张三的绩效怎么样" → 谓语 "绩效怎么样"
     * - "研发部门有哪些人"（无"的"）/ "研发部门的员工有多少"（主语是部门）→ 返回 null，
     *   避免拼出"于娟的研发部门有哪些人"这类病句。
     * </p>
     */
    private String extractPredicateFromPrevious(String previousQuery) {
        int deIdx = previousQuery.indexOf('的');
        if (deIdx <= 0 || deIdx > MAX_NAME_PREFIX_CHARS) {
            return null;
        }
        String subject = previousQuery.substring(0, deIdx).trim();
        String predicate = previousQuery.substring(deIdx + 1).trim();
        if (predicate.isEmpty() || !isInheritableSubject(subject)) {
            return null;
        }
        return predicate;
    }

    /**
     * 判断上一轮"的"之前的主语是否可以作为本轮人名的替换对象。
     */
    private boolean isInheritableSubject(String subject) {
        if (subject.isBlank() || subject.length() > MAX_NAME_PREFIX_CHARS) {
            return false;
        }
        if (SUBJECT_PRONOUNS.contains(subject)) {
            return true;
        }
        if (!CHINESE_NAME_PATTERN.matcher(subject).matches()) {
            return false;
        }
        if (isBusinessOrGreeting(subject)) {
            return false;
        }
        for (String suffix : ORG_SUFFIXES) {
            if (subject.endsWith(suffix)) {
                return false;
            }
        }
        for (char c : NAME_EXCLUDED_CHARS.toCharArray()) {
            if (subject.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** 业务名词/寒暄词都不是人名 */
    private boolean isBusinessOrGreeting(String text) {
        return BUSINESS_NOUNS.contains(text) || GREETING_WORDS.contains(text);
    }

    /** 谓语里必须含指标线索，才值得被下一轮继承 */
    private boolean hasMetricCue(String text) {
        for (String cue : METRIC_CUES) {
            if (text.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Tier 1: 规则触发器 ====================

    /**
     * 检测文本中是否含独立代词（排除复合词中的代词字符）。
     * <p>
     * 算法：先剥离已知复合词（其他/因此/其中等），再对剩余文本检查是否含代词。
     * 例如：
     * - "其他国家" → 剥离"其他" → "国家" → 无代词 → false
     * - "他的绩效" → 无复合词可剥离 → "他的绩效" → 含"他" → true
     * - "查看其他和他的绩效" → 剥离"其他" → "查看和他的绩效" → 含"他" → true
     * </p>
     */
    private boolean containsStandalonePronoun(String text) {
        String stripped = text;
        for (String compound : PRONOUN_COMPOUND_WORDS) {
            stripped = stripped.replace(compound, "");
        }
        if (stripped.isEmpty()) {
            return false;
        }
        return PRONOUN_PATTERN.matcher(stripped).find();
    }

    // ==================== Tier 2: 高置信快速路径 ====================

    /**
     * 尝试规则快速路径消解。
     * 仅处理高置信度模式（该员工/该部门/刚才那些/刚才的），
     * 匹配失败返回 null，由上层交给 LLM 处理。
     *
     * @return 快速路径结果，或 null 表示快速路径未匹配
     */
    private ResolveResult tryRuleFastPath(String query, List<String> historyTurns) {
        String[] patterns = policy.getHighConfidencePatterns();
        if (patterns == null || patterns.length == 0) {
            return null;
        }

        String resolved = query;
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String pattern : patterns) {
            if (!resolved.contains(pattern)) {
                continue;
            }
            String category = PATTERN_TO_CATEGORY.getOrDefault(pattern, "unknown");
            String entity = findMatchingEntity(historyTurns, category);
            if (entity == null || entity.isBlank()) {
                continue;
            }
            resolved = resolved.replace(pattern, entity);
            pairs.put(pattern, entity);
        }

        if (pairs.isEmpty()) {
            return null;
        }
        log.debug("规则快速路径消解: pairs={}, query={}", pairs, query);
        return ResolveResult.ruleResult(resolved, pairs);
    }

    /**
     * 根据类别从历史中提取最近提及的匹配实体。
     *
     * @param historyTurns 对话历史（最新在前）
     * @param category     实体类别（person/department/recent_entity）
     * @return 匹配的实体名，未找到返回 null
     */
    private String findMatchingEntity(List<String> historyTurns, String category) {
        int windowSize = Math.min(historyTurns.size(), AgentConstants.COREFERENCE_HISTORY_WINDOW);

        for (int i = 0; i < windowSize; i++) {
            String turn = historyTurns.get(i);
            if (turn == null || turn.isBlank()) {
                continue;
            }

            switch (category) {
                case "person" -> {
                    // 优先匹配 "X的绩效/考勤/简历..." 模式中的 X
                    String personFromDe = extractPersonFromDePattern(turn);
                    if (personFromDe != null) {
                        return personFromDe;
                    }
                    // 其次匹配 "X+职位后缀" 模式
                    String personFromSuffix = extractEntityBySuffix(turn, true);
                    if (personFromSuffix != null) {
                        return personFromSuffix;
                    }
                }
                case "department" -> {
                    // 匹配 "X部门/团队/组/中心/事业部" 模式
                    String dept = extractEntityBySuffix(turn, false);
                    if (dept != null) {
                        return dept;
                    }
                }
                case "recent_entity" -> {
                    // 返回历史中最近提及的任何实体
                    String person = extractPersonFromDePattern(turn);
                    if (person != null) {
                        return person;
                    }
                    String entity = extractEntityBySuffix(turn, false);
                    if (entity != null) {
                        return entity;
                    }
                }
                default -> {
                    // 未知类别，尝试通用提取
                    String entity = extractEntityBySuffix(turn, false);
                    if (entity != null) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 从 "X的绩效/考勤/简历..." 模式中提取 X 作为人名。
     * 提取后清理前导噪声动词（查看/查询等），确保返回纯实体名。
     */
    private String extractPersonFromDePattern(String text) {
        Matcher matcher = DE_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = stripLeadingNoise(matcher.group(1));
            if (isPersonCandidate(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 人名候选：2~6 个汉字，且不是业务名词/组织名（避免把"研发部门"当人） */
    private boolean isPersonCandidate(String candidate) {
        if (candidate == null || candidate.length() < 2 || candidate.length() > MAX_NAME_PREFIX_CHARS) {
            return false;
        }
        if (!CHINESE_NAME_PATTERN.matcher(candidate).matches()) {
            return false;
        }
        if (isBusinessOrGreeting(candidate)) {
            return false;
        }
        for (String suffix : ORG_SUFFIXES) {
            if (candidate.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 通过后缀匹配提取实体（人名或组织名）。
     * <p>
     * 用正向匹配"若干汉字 + 后缀"，取最短候选：既能剥掉"查一下研发部门"的动词前缀，
     * 也不会把"研发部门和市场部门"整体当成一个实体。
     * </p>
     *
     * @param text       历史文本
     * @param personOnly 是否仅提取人名（true 则只匹配职位后缀，false 则匹配所有后缀）
     * @return 提取的实体名，未找到返回 null
     */
    private String extractEntityBySuffix(String text, boolean personOnly) {
        List<String> suffixes = personOnly
                ? List.of("经理", "总监", "主管", "工程师", "分析师")
                : ENTITY_SUFFIXES;

        String best = null;
        for (String suffix : suffixes) {
            Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{1,6})" + Pattern.quote(suffix)).matcher(text);
            while (matcher.find()) {
                String candidate = stripLeadingNoise(matcher.group(1) + suffix);
                if (!candidate.endsWith(suffix) || candidate.equals(suffix)) {
                    continue;
                }
                if (best == null || candidate.length() < best.length()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * 剥离实体前缀上的查询动词与虚词，最多迭代三次（如"请帮我看看产品团队"）。
     */
    private String stripLeadingNoise(String candidate) {
        String value = candidate == null ? "" : candidate.trim();
        for (int i = 0; i < 3; i++) {
            String next = LEADING_NOISE.matcher(value).replaceFirst("").trim();
            if (next.equals(value)) {
                return value;
            }
            value = next;
        }
        return value;
    }
}
