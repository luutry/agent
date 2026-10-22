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
@TableName("hr_employee_work_history")
public class EmployeeWorkHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String companyName;
    private String department;
    private String position;
    private LocalDate startDate;
    private LocalDate endDate;
    private String leaveReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
