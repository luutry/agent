package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 补贴实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_subsidy")
public class Subsidy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private String subsidyType;
    private BigDecimal amount;
    private String yearMonth;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
