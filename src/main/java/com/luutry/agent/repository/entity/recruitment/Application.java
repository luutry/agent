package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投递记录实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_application")
public class Application {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Long requisitionId;
    private LocalDate applyDate;
    private String status;
    private String rejectReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
