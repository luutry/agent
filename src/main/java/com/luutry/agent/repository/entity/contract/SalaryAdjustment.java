package com.luutry.agent.repository.entity.contract;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_salary_adjustment")
public class SalaryAdjustment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long employeeId;
    private BigDecimal oldSalary;
    private BigDecimal newSalary;
    private String adjustType;
    private LocalDate effectiveDate;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
