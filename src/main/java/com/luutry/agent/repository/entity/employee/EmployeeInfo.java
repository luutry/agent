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
@TableName("hr_employee_info")
public class EmployeeInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String nationality;
    private String ethnicity;
    private Integer maritalStatus;
    private String politicalStatus;
    private String nativePlace;
    private String currentAddress;
    private String hukouAddress;
    private String highestEducation;
    private String major;
    private LocalDate graduationDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
