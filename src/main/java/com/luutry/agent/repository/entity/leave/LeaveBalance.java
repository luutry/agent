package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 假期余额快照实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_balance")
public class LeaveBalance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long leaveTypeId;
    private LocalDate snapshotDate;
    private BigDecimal totalQuota;
    private BigDecimal usedQuota;
    private BigDecimal remainingQuota;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
