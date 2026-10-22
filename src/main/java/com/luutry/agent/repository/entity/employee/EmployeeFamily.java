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
@TableName("hr_employee_family")
public class EmployeeFamily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String memberName;
    private String relationship;
    private Integer gender;
    private LocalDate birthDate;
    private String idNumber;
    private String phone;
    private String workUnit;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
