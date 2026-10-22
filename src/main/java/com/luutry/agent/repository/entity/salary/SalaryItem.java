package com.luutry.agent.repository.entity.salary;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 薪资项目实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("hr_salary_item")
public class SalaryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemName;
    private String itemCode;
    private String itemType;
    private String calcMethod;
    private Integer isTaxable;
    private Integer isVisible;
    private String description;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
