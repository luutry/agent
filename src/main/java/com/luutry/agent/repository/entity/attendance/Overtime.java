package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_overtime")
public class Overtime {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long deptId;
    private LocalDate overtimeDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal overtimeHours;
    private String overtimeType;
    private String compensation;
    private String approvalStatus;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
