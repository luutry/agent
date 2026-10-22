package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖金实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_bonus")
public class Bonus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long deptId;
    private String bonusType;
    private BigDecimal amount;
    private String yearMonth;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
