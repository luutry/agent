package com.luutry.agent.repository.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_employee_history")
public class EmployeeHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Integer versionNo;
    private String snapshotData;
    private String changeType;
    private String changeReason;
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
