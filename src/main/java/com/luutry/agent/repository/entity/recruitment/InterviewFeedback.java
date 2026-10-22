package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试评价实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_interview_feedback")
public class InterviewFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interviewId;
    private Long interviewerId;
    private BigDecimal score;
    private String evaluation;
    private String recommendation;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
