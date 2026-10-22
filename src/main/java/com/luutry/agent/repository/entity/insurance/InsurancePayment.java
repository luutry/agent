package com.luutry.agent.repository.entity.insurance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度缴费明细实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_insurance_payment")
public class InsurancePayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enrollmentId;
    private String yearMonth;
    private BigDecimal pensionCorp;
    private BigDecimal pensionEmp;
    private BigDecimal medicalCorp;
    private BigDecimal medicalEmp;
    private BigDecimal unemploymentCorp;
    private BigDecimal unemploymentEmp;
    private BigDecimal injuryCorp;
    private BigDecimal maternityCorp;
    private BigDecimal housingCorp;
    private BigDecimal housingEmp;
    private BigDecimal totalCorp;
    private BigDecimal totalEmp;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
