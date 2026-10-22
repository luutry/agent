package com.luutry.agent.repository.entity.insurance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 参保方案实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_insurance_scheme")
public class InsuranceScheme {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schemeName;
    private String city;
    private String schemeType;
    private LocalDate effectiveDate;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
