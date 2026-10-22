package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度薪资实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_monthly")
public class SalaryMonthly {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long deptId;
    private String yearMonth;
    private BigDecimal baseSalary;
    private BigDecimal allowanceTotal;
    private BigDecimal bonusTotal;
    private BigDecimal overtimePay;
    private BigDecimal grossSalary;
    private BigDecimal deductionTotal;
    private BigDecimal netSalary;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
