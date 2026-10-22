/**
 * Planner任务规划器 - 分支决策包
 * <p>
 * 负责任务执行过程中的分支决策，包括：
 * - 条件判断逻辑
 * - 分支路径选择
 * - 动态决策生成
 * - 终止条件判断
 * </p>
 * <p>
 * 重点防止RAG/Tool无限循环调用，必须设置最大轮次限制
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.planner.decision;
