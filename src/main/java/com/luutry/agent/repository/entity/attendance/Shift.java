package com.luutry.agent.repository.entity.attendance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_shift")
public class Shift {
    @TableId(type = IdType.AUTO) private Long id;
    private String shiftName;
    private String shiftCode;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer isCrossDay;
    private Integer lateMinutes;
    private Integer status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
