package com.luutry.agent.repository.entity.recruitment;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 简历实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_resume")
public class Resume {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private String resumeType;
    private String fileUrl;
    private String contentText;
    private LocalDate uploadDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
