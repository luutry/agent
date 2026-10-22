package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人才库实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_talent_pool")
public class TalentPool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Long channelId;
    private String tags;
    private String remark;
    private LocalDate addedDate;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
