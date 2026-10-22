package com.luutry.agent.aggregate.model;

/**
 * 结构化事实三元组，作为合成与校验的唯一真相源。
 * <p>
 * 用途：以 (entity, metric, value, source) 表达一条原子事实，直接从 ToolResult 结构化数据构造，
 * 不经文本序列化，避免"两份真相"导致的校验误判（R5）。
 * </p>
 *
 * @param entity 实体名（如部门名"技术部"、人名"张三"）
 * @param metric 指标名（如 turnoverRate、headcount）
 * @param value  归一化后的原始值字符串（如 0.08，不做百分比放大）
 * @param source 来源标识（如 TOOL、RAG:文档#章节）
 */
public record Fact(String entity, String metric, String value, String source) {

    /**
     * 从原始值构造 Fact，数值统一转为不带千分位的字符串，百分比字段保持小数原值。
     *
     * @param entity    实体名
     * @param metric    指标名
     * @param rawValue  原始值（Number/String/Boolean）
     * @param source    来源标识
     * @return 构造出的 Fact；rawValue 为 null 时返回 null
     */
    public static Fact of(String entity, String metric, Object rawValue, String source) {
        if (rawValue == null) {
            return null;
        }
        return new Fact(entity, metric, String.valueOf(rawValue), source);
    }

    /**
     * 事实唯一键 (entity#metric)，用于渲染—校验的集合比对（复审 N5）。
     */
    public String key() {
        return entity + "#" + metric;
    }

    /**
     * 将 value 解析为 double，无法解析时返回 NaN。
     */
    public double numericValue() {
        if (value == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * 判断该事实的数值是否在给定容差内等于目标值。
     */
    public boolean valueEquals(double target, double tolerance) {
        double v = numericValue();
        return !Double.isNaN(v) && Math.abs(v - target) <= tolerance;
    }
}
