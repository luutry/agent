package com.luutry.agent.tool.schema;

import com.luutry.agent.tool.model.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工具入参校验器
 * <p>
 * 用途：根据ToolDefinition中定义的inputSchema，校验ToolRequest传入的parameters是否合法。
 * 处理问题：Planner构造的工具调用参数可能缺失必填字段、类型不匹配，
 * 如果不校验直接执行会导致工具内部报错，难以定位问题根因。
 * 处理方式：
 * 1. 解析inputSchema中每个字段的类型描述（格式如"string, required"或"int, optional"）；
 * 2. 检查required字段是否存在于parameters中；
 * 3. 检查已提供字段的值类型是否与schema定义匹配；
 * 4. 返回校验错误列表，空列表表示校验通过。
 * </p>
 */
public final class ToolInputValidator {

    private static final String REQUIRED_MARKER = "required";
    private static final String TYPE_SEPARATOR = ",";

    private ToolInputValidator() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 根据工具入参Schema校验工具请求参数。
     *
     * @param definition 包含入参Schema的工具定义
     * @param parameters 工具请求的实际参数
     * @return 校验错误信息列表；空列表表示校验通过
     */
    public static List<String> validate(ToolDefinition definition, Map<String, Object> parameters) {
        if (definition == null) {
            return List.of("Tool definition is null");
        }
        Map<String, String> schema = definition.getInputSchema();
        if (schema == null || schema.isEmpty()) {
            return Collections.emptyList();
        }
        if (parameters == null) {
            parameters = Collections.emptyMap();
        }

        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> schemaEntry : schema.entrySet()) {
            String paramName = schemaEntry.getKey();
            String paramSpec = schemaEntry.getValue();

            boolean isRequired = isRequired(paramSpec);
            String expectedType = extractType(paramSpec);

            if (!parameters.containsKey(paramName) || parameters.get(paramName) == null) {
                if (isRequired) {
                    errors.add("Missing required parameter: " + paramName);
                }
                continue;
            }

            Object value = parameters.get(paramName);
            if (!matchesType(value, expectedType)) {
                errors.add("Parameter '" + paramName + "' expected type '"
                        + expectedType + "' but got '" + value.getClass().getSimpleName() + "'");
            }
        }

        return errors;
    }

    /**
     * 检查参数规格是否标记为必填
     *
     * @param paramSpec 参数规格字符串（如 "string, required"）
     * @return 必填时返回true
     */
    private static boolean isRequired(String paramSpec) {
        if (paramSpec == null || paramSpec.isEmpty()) {
            return false;
        }
        return paramSpec.toLowerCase().contains(REQUIRED_MARKER);
    }

    /**
     * 从参数规格中提取类型名称
     *
     * @param paramSpec 参数规格字符串（如 "string, required"）
     * @return 小写类型名（如 "string"），未指定时默认为"string"
     */
    private static String extractType(String paramSpec) {
        if (paramSpec == null || paramSpec.isEmpty()) {
            return "string";
        }
        String[] parts = paramSpec.split(TYPE_SEPARATOR);
        return parts[0].trim().toLowerCase();
    }

    /**
     * 检查实际值是否匹配期望类型。
     * 支持：string、int/integer、long、double/number/float、boolean、list、map。
     *
     * @param value        实际参数值
     * @param expectedType 期望类型名称
     * @return 值匹配期望类型时返回true
     */
    private static boolean matchesType(Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "int", "integer" -> value instanceof Integer || value instanceof Long;
            case "long" -> value instanceof Long || value instanceof Integer;
            case "double", "number", "float" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "list" -> value instanceof List;
            case "map" -> value instanceof Map;
            default -> true;
        };
    }
}
