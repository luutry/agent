package com.luutry.agent.preprocessor.router;

import com.luutry.agent.common.constant.AgentConstants;
import com.luutry.agent.preprocessor.model.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则式复合查询拆分：只在证据足够时拆分，宁可漏拆不可错拆。
 * 触发条件见 TODO-009：多问号、显式并列标记、逗号/连词两侧各有独立谓语、"分别"枚举。
 * 判断依据是"每个片段是否有独立谓语线索"，用线索词近似，避免引入分词依赖。
 */
@Slf4j
@Component
public class RuleBasedTaskDecomposer implements TaskDecomposer {

    /** 显式并列标记：出现即表示"另一件事" */
    private static final Pattern MARKER_PATTERN = Pattern.compile("另外|此外|又一个问题|再问一下|还想问");

    /** 问号 */
    private static final Pattern QUESTION_MARK_PATTERN = Pattern.compile("[？?]+");

    /** 逗号 / 分号 */
    private static final Pattern CLAUSE_SEPARATOR = Pattern.compile("[，,；;]+");

    /** 强连词：语义上几乎总是连接两件事 */
    private static final Pattern STRONG_CONJUNCTION = Pattern.compile("并且|同时|以及");

    /** 弱连词：需要两侧都有独立谓语才拆，避免并列宾语被误拆 */
    private static final Pattern WEAK_CONJUNCTION = Pattern.compile("和|与|及|还有");

    /** "分别/各自"枚举：前置枚举项 + 共享谓语 */
    private static final Pattern FENBIE_PATTERN = Pattern.compile("^(.+?)(?:分别|各自)(.+)$");

    /** 枚举项分隔符 */
    private static final Pattern ITEM_SEPARATOR = Pattern.compile("[、,，和与及]+");

    /**
     * 独立谓语的线索：疑问词 + 动作词。
     * 只认"能独立成问"的词，不把情况/区别/名单等名词当谓语，避免并列宾语被误拆。
     */
    private static final List<String> PREDICATE_CUES = List.of(
            "多少", "几", "多久", "哪", "谁", "什么", "怎么", "如何", "为什么", "是否", "有没有",
            "查", "看", "列出", "显示", "告诉", "统计", "分析", "对比", "盘点");

    /** 片段首部的连接词，拆分后需要剥掉，避免污染单意图分类 */
    private static final Pattern LEADING_CONNECTOR =
            Pattern.compile("^(另外|此外|同时|并且|以及|还有|然后|再)");

    @Override
    public List<SubTask> decompose(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<String> fragments = split(query);
        if (fragments.size() <= 1) {
            return Collections.emptyList();
        }

        int limit = Math.min(fragments.size(), AgentConstants.MAX_SUB_TASK_COUNT);
        if (fragments.size() > limit) {
            // TODO(doc/TODO/013_复合查询超限截断声明.md): 超出上限仅截断并告警，尚未在答案中向用户声明
            log.warn("复合查询片段超限，仅保留前 {} 个: total={}", limit, fragments.size());
        }
        List<SubTask> subTasks = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            subTasks.add(new SubTask(i, fragments.get(i)));
        }
        return subTasks;
    }

    @Override
    public boolean isComposite(String query) {
        return !decompose(query).isEmpty();
    }

    /**
     * 按优先级尝试四种触发方式，任一路径产出 ≥2 个含独立谓语的片段即采用。
     */
    private List<String> split(String query) {
        List<String> byQuestion = independentOnly(splitByQuestionMarks(query));
        if (byQuestion.size() > 1) {
            return byQuestion;
        }

        List<String> byFenbie = independentOnly(splitByFenbie(query));
        if (byFenbie.size() > 1) {
            return byFenbie;
        }

        List<String> byClause = independentOnly(splitAndClean(query, CLAUSE_SEPARATOR));
        if (byClause.size() > 1) {
            return byClause;
        }

        List<String> byStrong = independentOnly(splitAndClean(query, STRONG_CONJUNCTION));
        if (byStrong.size() > 1) {
            return byStrong;
        }

        return independentOnly(splitAndClean(query, WEAK_CONJUNCTION));
    }

    /**
     * 多问号是强信号，但片段仍需各自含独立谓语（过滤"你好？"这类寒暄片段）。
     */
    private List<String> splitByQuestionMarks(String query) {
        long questionCount = query.chars().filter(c -> c == '？' || c == '?').count();
        if (questionCount < 2) {
            return Collections.emptyList();
        }
        List<String> fragments = new ArrayList<>();
        for (String raw : QUESTION_MARK_PATTERN.split(query)) {
            String cleaned = stripDecorations(raw);
            if (!cleaned.isBlank()) {
                fragments.add(cleaned);
            }
        }
        return fragments;
    }

    /**
     * "年假、病假分别几天" → "年假几天" / "病假几天"：把共享谓语补到每个枚举项。
     */
    private List<String> splitByFenbie(String query) {
        Matcher matcher = FENBIE_PATTERN.matcher(stripDecorations(query));
        if (!matcher.matches()) {
            return Collections.emptyList();
        }
        String itemsPart = matcher.group(1);
        String predicate = stripDecorations(matcher.group(2));
        if (predicate.isBlank()) {
            return Collections.emptyList();
        }

        List<String> items = new ArrayList<>();
        for (String raw : ITEM_SEPARATOR.split(itemsPart)) {
            String item = stripDecorations(raw);
            if (!item.isBlank()) {
                items.add(item);
            }
        }
        if (items.size() < 2 || items.size() > AgentConstants.MAX_SUB_TASK_COUNT) {
            return Collections.emptyList();
        }

        List<String> fragments = new ArrayList<>(items.size());
        for (String item : items) {
            fragments.add(item + predicate);
        }
        return fragments;
    }

    private List<String> splitAndClean(String query, Pattern separator) {
        List<String> fragments = new ArrayList<>();
        for (String raw : separator.split(query)) {
            String cleaned = stripDecorations(raw);
            if (!cleaned.isBlank()) {
                fragments.add(cleaned);
            }
        }
        return fragments;
    }

    /**
     * 只保留含独立谓语的片段；不足 2 个说明是共享谓语或寒暄，整体不拆。
     */
    private List<String> independentOnly(List<String> fragments) {
        if (fragments.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> independent = fragments.stream()
                .filter(this::hasIndependentPredicate)
                .toList();
        return independent.size() > 1 ? independent : Collections.emptyList();
    }

    private boolean hasIndependentPredicate(String fragment) {
        for (String cue : PREDICATE_CUES) {
            if (fragment.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去掉首尾标点与连接词，保留片段自身的提问内容。
     */
    private String stripDecorations(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = value.replaceAll("^[\\s，,；;、:：]+", "");
        value = LEADING_CONNECTOR.matcher(value).replaceFirst("");
        value = value.replaceAll("[\\s，,；;、:：？?]+$", "");
        return value.trim();
    }
}
