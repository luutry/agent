package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面试安排实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_interview")
public class Interview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long candidateId;
    private Long interviewerId;
    private LocalDateTime interviewDate;
    private String interviewType;
    private Integer roundNo;
    private String location;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
