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
@TableName("hr_org_change")
public class OrgChange {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String changeType;
    private Long deptId;
    private String changeDesc;
    private LocalDate effectiveDate;
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
