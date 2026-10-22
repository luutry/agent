package com.luutry.agent.repository.entity.insurance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 增减员记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_insurance_change")
public class InsuranceChange {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enrollmentId;
    private String changeType;
    private LocalDate effectiveDate;
    private String reason;
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
