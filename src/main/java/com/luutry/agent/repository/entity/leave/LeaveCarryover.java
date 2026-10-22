package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 假期结转记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_carryover")
public class LeaveCarryover {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long leaveTypeId;
    private Integer fromYear;
    private Integer toYear;
    private BigDecimal carryDays;
    private LocalDate expireDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
