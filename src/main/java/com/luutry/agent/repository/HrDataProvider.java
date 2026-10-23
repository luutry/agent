package com.luutry.agent.repository;

import java.util.List;
import java.util.Map;

/**
 * HR 业务数据提供者契约（工具层唯一数据来源）。
 * <p>
 * 对外标识统一用表中的 String 编码列（employee_code / dept_code），不暴露 Long 主键；
 * scope 为管辖部门编码列表，实现必须在 SQL 条件层下推过滤（R2）；
 * 返回的 Map 已完成出域脱敏（phone 掩码、idNumber/birthDate 不返回，R3）。
 * </p>
 */
public interface HrDataProvider {

    /** 全量员工（内部聚合用，出域仍由调用方按 scope 收敛） */
    List<Map<String, Object>> getEmployees();

    /** 按管辖部门编码查询员工（R2：scope 下推 SQL；scope 为空返回空列表） */
    List<Map<String, Object>> getEmployees(List<String> scopeDeptCodes);

    /** 按工号查询单个员工 */
    Map<String, Object> findEmployeeById(String employeeCode);

    /** 全量部门 */
    List<Map<String, Object>> getDepartments();

    /** 按管辖部门编码查询部门（R2） */
    List<Map<String, Object>> getDepartments(List<String> scopeDeptCodes);

    /** 按部门编码查询单个部门 */
    Map<String, Object> findDepartmentById(String deptCode);

    /** 查询某部门编码下的直接子部门 */
    List<Map<String, Object>> getSubDepartments(String parentDeptCode);

    /** 查询某部门编码下的直接子部门（R2 scope 下推） */
    List<Map<String, Object>> getSubDepartments(String parentDeptCode, List<String> scopeDeptCodes);

    /** 全量部门数（供截断声明检测） */
    int getTotalDepartmentCount();

    /** 全量绩效记录 */
    List<Map<String, Object>> getPerformanceRecords();

    /** 按工号查询绩效记录 */
    List<Map<String, Object>> getPerformanceByEmployeeId(String employeeCode);

    /** 按工号查询绩效记录（R2：员工所属部门须在 scope 内） */
    List<Map<String, Object>> getPerformanceByEmployeeId(String employeeCode, List<String> scopeDeptCodes);
}
