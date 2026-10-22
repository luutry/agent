package com.luutry.agent.common.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应包装类
 * <p>
 * 用途：所有分页查询接口返回值的统一封装，包含数据列表和分页元信息。
 * 处理问题：分页接口返回值格式不统一，前端需要额外约定total、pageNum等字段的位置。
 * 处理方式：封装list（当前页数据）、total（总记录数）、pageNum（当前页码）、pageSize（每页大小）；
 * 提供静态工厂方法 of(list, total, req) 和 empty() 简化创建。
 * </p>
 *
 * @param <T> the type of elements in the page
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public static <T> PageResponse<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return new PageResponse<>(list, total, pageNum, pageSize);
    }

    public static <T> PageResponse<T> empty(int pageNum, int pageSize) {
        return new PageResponse<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }
}
