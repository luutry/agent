package com.luutry.agent.aggregate;

import com.luutry.agent.aggregate.model.Fact;
import com.luutry.agent.aggregate.model.FactSet;
import com.luutry.agent.tool.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 ToolResult 结构化数据提取事实三元组。
 * <p>
 * 用途：把工具返回的原始结构化数据转为 FactSet，作为合成与校验的唯一真相源；
 * 不经文本序列化，从根本上消除"两份真相"（P0-3）。
 * 处理问题：工具返回常见两种形态——含实体记录列表（如 metrics:[{deptName,turnoverRate}]）
 * 与扁平单记录（如 {name,dept}）；两者都需还原出 (entity, metric, value)。
 * 处理方式：识别实体名键，遍历标量字段生成 Fact，跳过 Map/List 复合字段。
 * </p>
 */
@Component
public class FactExtractor {

    /** 实体名候选键，按优先级排列 */
    private static final List<String> ENTITY_KEYS = List.of(
            "deptName", "orgName", "name", "deptId", "employeeId");

    /**
     * 非业务指标字段：数据质量/计数类，不得进入 FactSet。
     * 原因：totalEmployees 是明细记录数，与 currentStaff（在职）、headcount（编制）语义重叠且数值互斥，
     * 三者同时入 FactSet 会使 (entity,value) 校验失去判别力（复审 N2）。
     */
    private static final Set<String> NON_BUSINESS_METRICS = Set.of("totalEmployees");

    private static final String SOURCE_TOOL = "TOOL";

    /**
     * 从工具结果列表提取事实集合。
     *
     * @param toolResults 工具执行结果（可为 null）
     * @return 提取出的 FactSet，无结构化数据时返回空集合
     */
    public FactSet extract(List<ToolResult> toolResults) {
        List<Fact> facts = new ArrayList<>();
        if (toolResults == null) {
            return FactSet.of(facts);
        }
        for (ToolResult result : toolResults) {
            if (result == null || !result.isSuccess() || result.getData() == null) {
                continue;
            }
            extractFromMap(result.getData(), facts);
        }
        return FactSet.of(facts);
    }

    /**
     * 从一个 data map 提取事实：若某字段是实体记录列表则逐条展开，
     * 若整个 map 本身就是单实体记录则直接抽取标量字段。
     */
    private void extractFromMap(Map<String, Object> data, List<Fact> facts) {
        boolean expandedNested = false;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                expandedNested = true;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> record) {
                        extractRecord(asStringMap(record), facts);
                    }
                }
            }
        }
        // 无嵌套实体列表时，尝试把整个 map 当作单条实体记录
        if (!expandedNested) {
            extractRecord(data, facts);
        }
    }

    /**
     * 从单条实体记录抽取标量字段为 Fact。
     */
    private void extractRecord(Map<String, Object> record, List<Fact> facts) {
        String entity = resolveEntity(record);
        if (entity == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // 跳过实体名键本身、非业务计数字段与复合结构（分布 Map / 嵌套 List）
            if (ENTITY_KEYS.contains(key) || NON_BUSINESS_METRICS.contains(key)
                    || value instanceof Map || value instanceof List) {
                continue;
            }
            if (value instanceof Number || value instanceof Boolean
                    || (value instanceof String s && !s.isBlank())) {
                Fact fact = Fact.of(entity, key, value, SOURCE_TOOL);
                if (fact != null) {
                    facts.add(fact);
                }
            }
        }
    }

    /**
     * 按候选键优先级解析实体名。
     */
    private String resolveEntity(Map<String, Object> record) {
        for (String key : ENTITY_KEYS) {
            Object v = record.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
