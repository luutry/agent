package com.luutry.agent.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * JSON序列化工具类
 * <p>
 * 用途：基于Fastjson2提供统一的JSON序列化/反序列化能力，供全项目各模块使用。
 * 处理问题：各模块直接调用JSON库，配置不一致（如日期格式、null值处理），且异常处理散落各处。
 * 处理方式：
 * 1. 统一序列化配置：保留null值输出，确保字段缺失可被感知；
 * 2. 反序列化使用fastjson2默认配置（已内置单引号兼容支持）；
 * 3. 所有异常内部捕获后返回null，避免JSON异常向上传播导致业务中断。
 * </p>
 */
@Slf4j
public final class JsonUtils {

    private static final JSONWriter.Feature[] WRITE_FEATURES = {
            JSONWriter.Feature.WriteMapNullValue
    };

    private JsonUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 将对象序列化为JSON字符串
     *
     * @param obj 目标对象
     * @return JSON字符串，obj为null时返回"null"
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        return JSON.toJSONString(obj, WRITE_FEATURES);
    }

    /**
     * 将JSON字符串反序列化为目标类型
     *
     * @param json JSON字符串
     * @param clazz 目标类类型
     * @param <T> 目标类型
     * @return 反序列化后的对象，解析失败时返回null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, clazz);
        } catch (Exception e) {
            log.error("JSON deserialize error, json={}, class={}", json, clazz.getName(), e);
            return null;
        }
    }

    /**
     * 将 JSON 数组反序列化为目标类型列表。
     * <p>
     * 不记录原始 JSON，避免制度正文等大文本（可能含敏感信息）进入日志（R4）。
     * </p>
     *
     * @param json  JSON 数组字符串
     * @param clazz 元素类型
     * @param <T>   元素类型
     * @return 反序列化列表；输入为空或解析失败时返回空列表
     */
    public static <T> List<T> fromJsonList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<T> list = JSON.parseArray(json, clazz);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("JSON list deserialize error, class={}", clazz.getName(), e);
            return Collections.emptyList();
        }
    }
}
