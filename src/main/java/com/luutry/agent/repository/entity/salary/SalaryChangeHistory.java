package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调薪历史实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_change_history")
public class SalaryChangeHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String changeType;
    private BigDecimal oldBase;
    private BigDecimal newBase;
    private BigDecimal changePct;
    private LocalDate effectiveDate;
    private String reason;
    private Long approverId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
