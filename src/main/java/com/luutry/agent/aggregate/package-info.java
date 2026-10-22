/**
 * 上下文聚合管理器包
 * <p>
 * 负责统一管理所有上下文信息，包括：
 * - Memory记忆合并
 * - RAG召回片段整合
 * - Tool返回结果聚合
 * - 优先级排序
 * - 去重处理
 * - 动态压缩裁剪
 * - Token预算控制
 * </p>
 * <p>
 * 区分信息权重：数据库Tool结果 > 最新RAG文档 > 历史记忆
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.aggregate;
