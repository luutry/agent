package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期流水实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_record")
public class LeaveRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long deptId;
    private Long leaveTypeId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private String reason;
    private Long shiftId;
    private String proofUrl;
    private String approvalStatus;
    private Long approverId;
    private LocalDateTime approveTime;
    private String rejectReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
