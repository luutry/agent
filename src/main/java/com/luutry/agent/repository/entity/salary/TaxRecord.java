package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 个税计算实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_tax_record")
public class TaxRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String yearMonth;
    private BigDecimal taxableIncome;
    private BigDecimal taxRate;
    private BigDecimal quickDeduction;
    private BigDecimal taxAmount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
