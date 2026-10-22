package com.luutry.agent.aggregate.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 事实集合，承载一次执行产生的全部结构化事实三元组。
 * <p>
 * 用途：作为合成与校验共享的唯一真相源；校验时按 (entity, value) 精确比对，
 * 可识别"数字对、归属错"的张冠李戴（R5），不做模糊放行。
 * </p>
 */
public class FactSet {

    /** 数值比对容差，覆盖 0.08 与 8.0% 归一化后的浮点误差 */
    public static final double VALUE_TOLERANCE = 0.0001;

    private final List<Fact> facts;

    public FactSet(List<Fact> facts) {
        this.facts = facts != null
                ? Collections.unmodifiableList(new ArrayList<>(facts))
                : Collections.emptyList();
    }

    public static FactSet empty() {
        return new FactSet(Collections.emptyList());
    }

    public static FactSet of(List<Fact> facts) {
        return new FactSet(facts);
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }

    public int size() {
        return facts.size();
    }

    public List<Fact> getFacts() {
        return facts;
    }

    /**
     * 获取全部实体名（去重、保序）。
     */
    public Set<String> entities() {
        Set<String> result = new LinkedHashSet<>();
        for (Fact f : facts) {
            if (f.entity() != null) {
                result.add(f.entity());
            }
        }
        return result;
    }

    /**
     * 按指标名筛选事实。
     */
    public List<Fact> byMetric(String metric) {
        List<Fact> result = new ArrayList<>();
        for (Fact f : facts) {
            if (f.metric() != null && f.metric().equals(metric)) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * 判断某实体是否拥有给定数值（任一指标匹配即可）。
     * 用于张冠李戴检测：答案把值 V 归给实体 E，当且仅当 E 确有值 V 才算被支持。
     *
     * @param entity           实体名
     * @param normalizedValue  归一化后的数值（百分比已转小数）
     * @return 该实体拥有此数值时返回 true
     */
    public boolean containsEntityValue(String entity, double normalizedValue) {
        if (entity == null) {
            return false;
        }
        for (Fact f : facts) {
            if (entity.equals(f.entity()) && f.valueEquals(normalizedValue, VALUE_TOLERANCE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 全部事实键 (entity#metric) 去重集合，供渲染时绑定、校验时做集合比对（复审 N5）。
     */
    public Set<String> keys() {
        Set<String> result = new LinkedHashSet<>();
        for (Fact f : facts) {
            result.add(f.key());
        }
        return result;
    }

    /**
     * 收集全部数值型事实的归一化值（去重）。
     */
    public Set<Double> allNumericValues() {
        Set<Double> values = new LinkedHashSet<>();
        for (Fact f : facts) {
            double v = f.numericValue();
            if (!Double.isNaN(v)) {
                values.add(v);
            }
        }
        return values;
    }
}
