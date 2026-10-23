package com.luutry.agent.repository.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * HR 扁平化查询 Mapper（多表 join，返回工具消费的 Map 结构）。
 * <p>
 * 独立于 BaseMapper 实体查询：此处一次性 join 出岗位/职级/部门编码/最新绩效等级，
 * 并把数据权限 scope 下推到 SQL 的 dept_code IN(...) 条件（R2），避免查完再过滤。
 * </p>
 */
public interface HrQueryMapper {

    List<Map<String, Object>> selectEmployees(@Param("scope") List<String> scope);

    Map<String, Object> selectEmployeeByCode(@Param("employeeCode") String employeeCode);

    List<Map<String, Object>> selectDepartments(@Param("scope") List<String> scope);

    Map<String, Object> selectDepartmentByCode(@Param("deptCode") String deptCode);

    List<Map<String, Object>> selectSubDepartments(@Param("parentDeptCode") String parentDeptCode,
                                                   @Param("scope") List<String> scope);

    int countDepartments();

    List<Map<String, Object>> selectPerformanceByEmployeeCode(@Param("employeeCode") String employeeCode,
                                                              @Param("scope") List<String> scope);

    List<Map<String, Object>> selectAllPerformance();
}
