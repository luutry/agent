package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 薪资档案实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_archive")
public class SalaryArchive {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private BigDecimal baseSalary;
    private BigDecimal positionAllowance;
    private BigDecimal performanceBase;
    private BigDecimal otherAllowance;
    private String bankName;
    private String bankAccount;
    private LocalDate effectiveDate;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
