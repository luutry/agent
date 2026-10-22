package com.luutry.agent.repository.entity.performance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_evaluation_score")
public class EvaluationScore {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long employeeId;
    private Long indicatorId;
    private BigDecimal score;
    private String comment;
    private String scorerType;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
