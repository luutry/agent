package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入职办理实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_onboarding")
public class Onboarding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Long offerId;
    private Long deptId;
    private Long positionId;
    private LocalDate entryDate;
    private String onboardingStatus;
    private Integer idCardCollected;
    private Integer contractSigned;
    private Integer accountCreated;
    private Integer equipmentIssued;
    private Integer trainingDone;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
