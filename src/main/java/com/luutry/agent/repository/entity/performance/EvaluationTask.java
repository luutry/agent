package com.luutry.agent.repository.entity.performance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_evaluation_task")
public class EvaluationTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long planId;
    private Long cycleId;
    private Long deptId;
    private String taskName;
    private String status;
    private LocalDate deadline;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
