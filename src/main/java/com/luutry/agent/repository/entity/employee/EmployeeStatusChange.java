package com.luutry.agent.repository.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_employee_status_change")
public class EmployeeStatusChange {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String changeType;
    private String oldStatus;
    private String newStatus;
    private Long oldDeptId;
    private Long newDeptId;
    private Long oldPositionId;
    private Long newPositionId;
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
