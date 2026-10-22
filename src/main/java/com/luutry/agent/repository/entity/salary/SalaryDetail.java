package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 薪资明细实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_detail")
public class SalaryDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monthlyId;
    private Long itemId;
    private BigDecimal amount;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
