package com.luutry.agent.repository.entity.leave;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期类型实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_leave_type")
public class LeaveType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeName;
    private String typeCode;
    private Integer isPaid;
    private BigDecimal maxDays;
    private String unit;
    private Integer needProof;
    private String description;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
