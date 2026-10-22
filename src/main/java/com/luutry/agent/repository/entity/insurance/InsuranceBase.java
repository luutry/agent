package com.luutry.agent.repository.entity.insurance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 缴费基数实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_insurance_base")
public class InsuranceBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enrollmentId;
    private Integer year;
    private BigDecimal pensionBase;
    private BigDecimal medicalBase;
    private BigDecimal unemploymentBase;
    private BigDecimal injuryBase;
    private BigDecimal maternityBase;
    private BigDecimal housingBase;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
