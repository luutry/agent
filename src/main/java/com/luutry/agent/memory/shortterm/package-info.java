/**
 * Memory记忆模块 - 短期会话记忆包
 * <p>
 * 负责存储当前对话的短期记忆，包括：
 * - 本轮对话原始问答
 * - 工具返回原始结果
 * - 临时上下文信息
 * </p>
 * <p>
 * 数据存放Redis，支持快速读写和自动过期
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.memory.shortterm;
