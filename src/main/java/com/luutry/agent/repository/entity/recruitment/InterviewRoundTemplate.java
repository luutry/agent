package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面试轮次模板实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_interview_round_template")
public class InterviewRoundTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private Long positionId;
    private Integer roundNo;
    private String roundType;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
