package com.luutry.agent.repository.entity.contract;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
@TableName("hr_contract_renewal")
public class ContractRenewal {
    @TableId(type = IdType.AUTO) private Long id;
    private Long contractId;
    private Long employeeId;
    private Integer renewalNo;
    private LocalDate oldEndDate;
    private LocalDate newEndDate;
    private LocalDate renewalDate;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
