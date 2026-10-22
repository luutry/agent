package com.luutry.agent.planner.synthesis;

import com.luutry.agent.aggregate.model.Fact;
import com.luutry.agent.aggregate.model.FactSet;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 纯数据类回答的确定性模板渲染器。
 * <p>
 * 用途：TOOL 意图的指标查询直接用 FactSet 渲染自然语言，不经 LLM 改写，
 * 从根本上消除幻觉面（P1-3），且可被 Fact 三元组精确校验。
 * 处理问题：LLM 改写数字会引入"数字对、归属错"的风险，还要再花检测器兜底，是负收益闭环。
 * 处理方式：按查询关键词解析目标指标 → 取该指标的全部事实 → 按实体渲染；
 * 无法确定指标时返回 empty，由调用方回退 LLM 合成。
 * </p>
 */
@Component
public class DataTemplateRenderer {

    /** 指标元数据：中文标签、是否比率（×100 加 %）、单位 */
    private record MetricMeta(String label, boolean rate, String unit) {}

    /**
     * 渲染产物：确定性文本 + 该文本实际用到的事实键集合。
     * 校验退化为 usedFactKeys ⊆ factSet.keys() 的集合比对，零自然语言解析（复审 N5）。
     */
    public record RenderResult(String text, Set<String> usedFactKeys) {}

    private static final Map<String, MetricMeta> METRICS = new LinkedHashMap<>();

    static {
        METRICS.put("turnoverRate", new MetricMeta("离职率", true, ""));
        METRICS.put("headcountUsageRate", new MetricMeta("编制使用率", true, ""));
        METRICS.put("probationPassRate", new MetricMeta("转正率", true, ""));
        METRICS.put("currentStaff", new MetricMeta("在职人数", false, "人"));
        METRICS.put("headcount", new MetricMeta("编制人数", false, "人"));
        METRICS.put("averageTenureYears", new MetricMeta("平均司龄", false, "年"));
    }

    /** 查询关键词 → 指标键，用于解析用户想问的指标 */
    private static final Map<String, String> KEYWORD_TO_METRIC = new LinkedHashMap<>();

    static {
        KEYWORD_TO_METRIC.put("离职率", "turnoverRate");
        KEYWORD_TO_METRIC.put("流失率", "turnoverRate");
        KEYWORD_TO_METRIC.put("编制使用率", "headcountUsageRate");
        KEYWORD_TO_METRIC.put("转正率", "probationPassRate");
        KEYWORD_TO_METRIC.put("平均司龄", "averageTenureYears");
        KEYWORD_TO_METRIC.put("司龄", "averageTenureYears");
        KEYWORD_TO_METRIC.put("在职人数", "currentStaff");
        KEYWORD_TO_METRIC.put("编制", "headcount");
        KEYWORD_TO_METRIC.put("人数", "currentStaff");
    }

    /**
     * 因果/比较/建议类词：这类查询需要解释而非数据堆，必须走 LLM，不走确定性模板（复审 N7）。
     */
    private static final List<String> CAUSAL_WORDS = List.of(
            "为什么", "为何", "原因", "怎么会", "哪个更", "哪些更", "最高", "最低", "最好", "最差",
            "建议", "怎么办", "分析", "解释", "对比", "比较", "排名", "趋势");

    /**
     * 时间限定词：工具数据是静态快照、不支持按时间筛选，命中时须在答案里显式声明口径（复审 N6）。
     * // TODO(doc/TODO/011_period时间维度与Mock数据口径.md): 真实实现应抽取 period 槽位下推工具，取代快照声明
     */
    private static final List<String> TIME_WORDS = List.of(
            "本月", "上月", "今年", "去年", "本季度", "上季度", "最近", "这月", "这个月");

    private static final String SNAPSHOT_DISCLAIMER = "（当前快照，不支持按时间筛选）";

    /**
     * 尝试根据查询与事实集合渲染确定性回答，并回传所用事实键。
     *
     * @param query   用户查询（用于解析目标指标）
     * @param factSet 事实集合
     * @return 渲染产物；无法确定指标或无对应事实时返回 empty
     */
    public Optional<RenderResult> render(String query, FactSet factSet) {
        if (query == null || factSet == null || factSet.isEmpty()) {
            return Optional.empty();
        }
        // N7：因果/比较/建议类查询需解释，回退 LLM，不用数据堆模板
        if (isCausalOrComparative(query)) {
            return Optional.empty();
        }
        String metricKey = resolveMetric(query);
        if (metricKey == null) {
            return Optional.empty();
        }
        List<Fact> facts = factSet.byMetric(metricKey);
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        MetricMeta meta = METRICS.get(metricKey);

        StringBuilder sb = new StringBuilder();
        Set<String> usedFactKeys = new LinkedHashSet<>();
        sb.append(meta.label()).append("：");
        for (int i = 0; i < facts.size(); i++) {
            Fact f = facts.get(i);
            if (i > 0) {
                sb.append("、");
            }
            sb.append(f.entity()).append(" ").append(formatValue(f, meta));
            usedFactKeys.add(f.key());
        }
        // N6：命中时间限定词时显式声明快照口径，不静默忽略用户的"本月"等限定（R5）
        if (hasTimeWord(query)) {
            sb.append(SNAPSHOT_DISCLAIMER);
        }
        return Optional.of(new RenderResult(sb.toString(), usedFactKeys));
    }

    /**
     * 查询是否含因果/比较/建议类词（需解释，不走模板）。
     */
    private boolean isCausalOrComparative(String query) {
        for (String w : CAUSAL_WORDS) {
            if (query.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询是否含时间限定词。
     */
    private boolean hasTimeWord(String query) {
        for (String w : TIME_WORDS) {
            if (query.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按查询关键词解析目标指标键，命中不到返回 null。
     */
    private String resolveMetric(String query) {
        for (Map.Entry<String, String> e : KEYWORD_TO_METRIC.entrySet()) {
            if (query.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 按指标元数据格式化数值：比率转百分比，其余保留原值并附单位。
     */
    private String formatValue(Fact fact, MetricMeta meta) {
        double v = fact.numericValue();
        if (Double.isNaN(v)) {
            return fact.value();
        }
        if (meta.rate()) {
            return trimZero(v * 100) + "%";
        }
        return trimZero(v) + meta.unit();
    }

    /**
     * 去除多余的尾随零：8.0 → 8，4.8 → 4.8。
     */
    private String trimZero(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 10) / 10.0);
    }
}
