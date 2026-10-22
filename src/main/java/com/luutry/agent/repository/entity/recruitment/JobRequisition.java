package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 职位需求实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_job_requisition")
public class JobRequisition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long positionId;
    private Integer headcount;
    private String urgency;
    private String jobDescription;
    private String requirements;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String status;
    private LocalDate requestDate;
    private LocalDate deadlineDate;
    private Long requesterId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
