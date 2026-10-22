package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_leave")
public class Leave {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long deptId;
    private String leaveType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private String reason;
    private Long shiftId;
    private String approvalStatus;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
