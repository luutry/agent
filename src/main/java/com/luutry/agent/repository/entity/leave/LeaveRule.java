package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期规则实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_rule")
public class LeaveRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long leaveTypeId;
    private String ruleName;
    private Integer minTenure;
    private Integer maxTenure;
    private BigDecimal annualQuota;
    private BigDecimal minIncrement;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
