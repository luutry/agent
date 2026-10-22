package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_business_trip")
public class BusinessTrip {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long deptId;
    private LocalDate tripStart;
    private LocalDate tripEnd;
    private String destination;
    private String purpose;
    private BigDecimal tripDays;
    private String approvalStatus;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
