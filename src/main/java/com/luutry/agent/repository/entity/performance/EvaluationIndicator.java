package com.luutry.agent.repository.entity.performance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_evaluation_indicator")
public class EvaluationIndicator {
    @TableId(type = IdType.AUTO) private Long id;
    private Long planId;
    private String indicatorName;
    private String indicatorCode;
    private String category;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private String description;
    private Integer status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
