package com.luutry.agent.preprocessor.rewriter;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于规则的查询改写器实现。
 * 使用同义词字典和HR术语标准化
 * 来扩展查询，提升检索覆盖度。
 */
@Component
public class RuleBasedQueryRewriter implements QueryRewriter {

    /** 同义词映射：关键词 → 空格分隔的同义词 */
    private static final Map<String, String> SYNONYM_MAP = Map.ofEntries(
            Map.entry("离职", "流失 辞退 解约 离开"),
            Map.entry("入职", "加入 新进 录用 报到"),
            Map.entry("考勤", "出勤 打卡 请假 缺勤"),
            Map.entry("绩效", "考核 考评 评分 业绩"),
            Map.entry("薪资", "薪酬 工资 待遇 收入"),
            Map.entry("晋升", "升职 提拔 晋级 提职"),
            Map.entry("调岗", "转岗 调动 调职 岗位变动"),
            Map.entry("编制", "hc headcount 名额 人员编制"),
            Map.entry("司龄", "工龄 在职时长 服务年限"),
            Map.entry("职级", "级别 等级 层级 岗位级别"),
            Map.entry("团队", "小组 部门 组织 团队"),
            Map.entry("简历", "履历 档案 经历 个人信息"),
            Map.entry("转正", "正式 转正申请 试用期转正"),
            Map.entry("员工", "人员 同事 职工 成员"),
            Map.entry("盘点", "梳理 清查 分析 统计"),
            Map.entry("看板", "报表 指标 dashboard 数据面板")
    );

    /** HR标准术语映射：非正式 → 正式 */
    private static final Map<String, String> STANDARD_TERM_MAP = Map.ofEntries(
            Map.entry("hc", "编制"),
            Map.entry("HC", "编制"),
            Map.entry("headcount", "编制"),
            Map.entry("kpi", "绩效考核"),
            Map.entry("KPI", "绩效考核"),
            Map.entry("okr", "目标管理"),
            Map.entry("OKR", "目标管理"),
            Map.entry("offer", "录用通知"),
            Map.entry("试用期", "试用考察期"),
            Map.entry("五险一金", "社会保险与公积金")
    );

    /** 触发同义词扩展的最小查询长度 */
    private static final int MIN_EXPAND_LENGTH = 2;

    @Override
    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        // 步骤1：标准化非正式HR术语
        String standardized = standardizeTerms(query);

        // 步骤2：为关键术语追加同义词（用于检索扩展）
        // 使用原始查询避免重新引入被标准化掉的术语
        return expandSynonyms(standardized, query);
    }

    @Override
    public List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> keywords = new LinkedHashSet<>();

        // 提取已知HR术语作为关键词
        for (String term : SYNONYM_MAP.keySet()) {
            if (query.contains(term)) {
                keywords.add(term);
            }
        }

        // 提取标准术语
        for (String term : STANDARD_TERM_MAP.keySet()) {
            if (query.contains(term)) {
                keywords.add(STANDARD_TERM_MAP.get(term));
            }
        }

        // 提取中文名词短语（2个及以上字符）作为额外关键词
        // 简单启发式：按常见助词和停用词分割
        String[] segments = query.split("[的了是在有和与及吗呢吧啊]");
        for (String seg : segments) {
            String trimmed = seg.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 8) {
                keywords.add(trimmed);
            }
        }

        return new ArrayList<>(keywords);
    }

    /**
     * 将非正式/缩写术语替换为标准HR术语。
     */
    private String standardizeTerms(String query) {
        String result = query;
        for (Map.Entry<String, String> entry : STANDARD_TERM_MAP.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 用同义词扩展关键术语，提升检索效果。
     * 在原始术语后追加括号内的同义词。
     *
     * @param query        待扩展的标准化查询
     * @param originalQuery 标准化前的原始查询
     */
    private String expandSynonyms(String query, String originalQuery) {
        if (query.length() < MIN_EXPAND_LENGTH) {
            return query;
        }

        List<String> expansions = new ArrayList<>();
        for (Map.Entry<String, String> entry : SYNONYM_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                // 只取前2个同义词，避免过度扩展
                String[] synonyms = entry.getValue().split(" ");
                for (int i = 0; i < Math.min(2, synonyms.length); i++) {
                    // 跳过已存在于标准化查询或原始查询中的同义词
                    // （防止重新引入刚被标准化掉的术语）
                    if (!query.contains(synonyms[i]) && !originalQuery.contains(synonyms[i])
                            && !expansions.contains(synonyms[i])) {
                        expansions.add(synonyms[i]);
                    }
                }
            }
        }

        if (expansions.isEmpty()) {
            return query;
        }

        // 最多限制4个扩展词
        String expansionStr = expansions.stream()
                .limit(4)
                .collect(Collectors.joining(" "));

        return query + " " + expansionStr;
    }
}
