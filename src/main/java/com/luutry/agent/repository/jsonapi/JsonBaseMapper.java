package com.luutry.agent.repository.jsonapi;

import java.util.List;
import java.util.Map;

/**
 * JSON 文件实现的通用 Mapper 接口（MyBatis-Plus 风格占位）。
 * 真实数据库接入后由 MyBatis-Plus BaseMapper 替代，调用方不改（R11）。
 */
public interface JsonBaseMapper<T> {

    List<T> selectAll();

    List<T> selectList(List<String> scope);

    T selectById(String id);

    List<T> selectByCondition(Map<String, Object> conditions, List<String> scope);
}
