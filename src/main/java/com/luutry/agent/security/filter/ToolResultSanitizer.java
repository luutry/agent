package com.luutry.agent.security.filter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具结果脱敏器：为即将进入 LLM 的结构化结果生成安全副本。
 * R3 要求 PII 不出域、R3.1 要求绩效明细不得进入 Prompt，故按字段名脱敏并整条剔除绩效明细。
 * 姓名与聚合绩效不脱敏：R3 未列姓名，R3.1 明确允许聚合值。
 */
@Component
public class ToolResultSanitizer {

    /**
     * 员工级绩效明细字段：整条不得进入任何 Prompt（R3.1）。
     * 注意 performanceDistribution / averageScore 属聚合统计，允许保留。
     */
    private static final Set<String> PERFORMANCE_DETAIL_KEYS = Set.of(
            "score", "grade", "comment", "appealed", "performancegrade", "latestperformance");

    /** 薪资类字段：无论类型统一替换为占位符。 */
    private static final Set<String> SECRET_KEYS = Set.of(
            "salary", "wage", "compensation", "bonus");

    /** 字段名 → PII 类型，使用既有脱敏规则，避免各处重复实现。 */
    private static final Map<String, PiiType> FIELD_TYPES = Map.of(
            "phone", PiiType.PHONE,
            "mobile", PiiType.PHONE,
            "email", PiiType.EMAIL,
            "idcard", PiiType.ID_CARD,
            "idcardno", PiiType.ID_CARD,
            "bankcard", PiiType.BANK_CARD);

    /** 工号字段：R3 明确要求脱敏，保留首字符便于排障定位。 */
    private static final Set<String> EMPLOYEE_ID_KEYS = Set.of(
            "employeeid", "empno", "employeeno");

    /**
     * 生成可安全送 LLM 的数据副本，不修改入参。
     *
     * @param data 工具原始结果
     * @return 已剔除绩效明细、已脱敏 PII 的新 Map；入参为 null 时返回空 Map
     */
    public Map<String, Object> sanitizeForLlm(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String normalizedKey = normalizeKey(entry.getKey());
            if (PERFORMANCE_DETAIL_KEYS.contains(normalizedKey)) {
                continue;
            }
            sanitized.put(entry.getKey(), sanitizeValue(normalizedKey, entry.getValue()));
        }
        return sanitized;
    }

    /**
     * 递归处理 Map/List，标量按字段名或内容脱敏。
     */
    private Object sanitizeValue(String normalizedKey, Object value) {
        if (value == null) {
            return null;
        }
        if (SECRET_KEYS.contains(normalizedKey)) {
            return "***";
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeForLlm(asStringMap(map));
        }
        if (value instanceof List<?> list) {
            List<Object> items = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> mapItem) {
                    items.add(sanitizeForLlm(asStringMap(mapItem)));
                } else if (item instanceof String text) {
                    items.add(PiiMaskerFactory.maskText(text));
                } else {
                    items.add(item);
                }
            }
            return items;
        }
        if (!(value instanceof String text)) {
            return value;
        }
        if (EMPLOYEE_ID_KEYS.contains(normalizedKey)) {
            return maskEmployeeId(text);
        }
        PiiType type = FIELD_TYPES.get(normalizedKey);
        if (type != null) {
            PiiMasker masker = PiiMaskerFactory.getMasker(type);
            if (masker != null) {
                return masker.mask(text);
            }
        }
        // 兜底：字段名不敏感时仍扫描文本内嵌的手机号/邮箱/证件号
        return PiiMaskerFactory.maskText(text);
    }

    /**
     * 工号脱敏：仅保留首字符（E001 → E***）。
     */
    private String maskEmployeeId(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.charAt(0) + "***";
    }

    /**
     * 归一化字段名，兼容 employeeId / employee_id / employeeid 三种写法。
     */
    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase().replace("_", "").replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
