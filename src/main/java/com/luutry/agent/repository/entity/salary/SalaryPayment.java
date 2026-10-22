package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 薪资发放记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_payment")
public class SalaryPayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monthlyId;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String bankName;
    private String bankSerial;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
