package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 薪资标准实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_standard")
public class SalaryStandard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long positionId;
    private Long levelId;
    private BigDecimal minSalary;
    private BigDecimal midSalary;
    private BigDecimal maxSalary;
    private LocalDate effectiveDate;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
