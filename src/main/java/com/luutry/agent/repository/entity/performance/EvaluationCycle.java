package com.luutry.agent.repository.entity.performance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_evaluation_cycle")
public class EvaluationCycle {
    @TableId(type = IdType.AUTO) private Long id;
    private String cycleName;
    private String cycleType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
