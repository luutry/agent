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
@TableName("hr_employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String employeeCode;
    private String employeeName;
    private Integer gender;
    private LocalDate birthDate;
    private String idNumber;
    private String phone;
    private String email;
    private Long deptId;
    private Long positionId;
    private Long levelId;
    private LocalDate entryDate;
    private LocalDate leaveDate;
    private String employmentType;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
