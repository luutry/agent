package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招聘渠道实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_recruitment_channel")
public class RecruitmentChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelName;
    private String channelCode;
    private String channelType;
    private String contactPerson;
    private String contactPhone;
    private BigDecimal costPerHire;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
