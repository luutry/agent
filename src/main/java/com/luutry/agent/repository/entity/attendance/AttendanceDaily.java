package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_attendance_daily")
public class AttendanceDaily {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long deptId;
    private LocalDate attendanceDate;
    private Long shiftId;
    private LocalDateTime clockIn;
    private LocalDateTime clockOut;
    private BigDecimal workHours;
    private String status;
    private Integer isLate;
    private Integer isEarly;
    private Integer isAbsent;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
