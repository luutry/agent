package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_offer")
public class Offer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Long requisitionId;
    private String offerNo;
    private Long positionId;
    private Long levelId;
    private Long deptId;
    private BigDecimal offeredSalary;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private String status;
    private LocalDate issueDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
