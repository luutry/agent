package com.luutry.agent.repository.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_employee_emergency")
public class EmployeeEmergency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String contactName;
    private String relationship;
    private String phone;
    private String address;
    private Integer isPrimary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
