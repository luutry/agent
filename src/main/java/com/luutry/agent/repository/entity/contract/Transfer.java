package com.luutry.agent.repository.entity.contract;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_transfer")
public class Transfer {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private Long oldDeptId;
    private Long newDeptId;
    private Long oldPositionId;
    private Long newPositionId;
    private Long oldLevelId;
    private Long newLevelId;
    private String transferType;
    private LocalDate effectiveDate;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
