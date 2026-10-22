package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期额度实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_quota")
public class LeaveQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long leaveTypeId;
    private Integer year;
    private BigDecimal totalQuota;
    private BigDecimal usedQuota;
    private BigDecimal remainingQuota;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
