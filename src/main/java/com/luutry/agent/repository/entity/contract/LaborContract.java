package com.luutry.agent.repository.entity.contract;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_labor_contract")
public class LaborContract {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private String contractNo;
    private String contractType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer probationDays;
    private LocalDate signDate;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
