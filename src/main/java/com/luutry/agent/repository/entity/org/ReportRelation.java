package com.luutry.agent.repository.entity.org;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_report_relation")
public class ReportRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long superiorId;
    private String relationType;
    private LocalDate startDate;
    private LocalDate endDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
