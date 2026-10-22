package com.luutry.agent.common.model.dto;

import com.luutry.agent.common.constant.AgentConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求基类
 * <p>
 * 用途：所有需要分页的查询接口的统一请求参数封装。
 * 处理问题：各分页接口重复定义pageNum/pageSize参数，缺少默认值和范围校验。
 * 处理方式：作为基类被各业务分页请求DTO继承；
 * 通过JSR-303注解限制pageNum≥1、pageSize在[1,100]范围内，提供默认值。
 * </p>
 */
@Data
public class PageRequest {

    @Min(value = 1, message = "页码必须 >= 1")
    private int pageNum = 1;

    @Min(value = 1, message = "每页大小必须 >= 1")
    @Max(value = AgentConstants.MAX_PAGE_SIZE, message = "每页大小必须 <= " + AgentConstants.MAX_PAGE_SIZE)
    private int pageSize = AgentConstants.DEFAULT_PAGE_SIZE;

    /**
     * 计算LIMIT子句的SQL偏移量
     *
     * @return offset = (pageNum - 1) * pageSize
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
