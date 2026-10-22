package com.luutry.agent.repository.entity.org;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_position_level")
public class PositionLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long positionId;
    private Long levelId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
