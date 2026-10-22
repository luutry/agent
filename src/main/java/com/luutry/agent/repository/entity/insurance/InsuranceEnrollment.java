package com.luutry.agent.repository.entity.insurance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 参保人员实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_insurance_enrollment")
public class InsuranceEnrollment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long schemeId;
    private LocalDate enrollmentDate;
    private LocalDate endDate;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
