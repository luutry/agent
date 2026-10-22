package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_attendance_monthly")
public class AttendanceMonthly {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long deptId;
    private String yearMonth;
    private Integer workDays;
    private BigDecimal actualDays;
    private Integer lateCount;
    private Integer earlyCount;
    private Integer absentCount;
    private BigDecimal leaveDays;
    private BigDecimal overtimeHours;
    private BigDecimal businessDays;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
