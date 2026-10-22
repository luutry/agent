package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 假期审批记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_approval")
public class LeaveApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long leaveRecordId;
    private Long approverId;
    private Integer approvalOrder;
    private String action;
    private String comment;
    private LocalDateTime actionTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
