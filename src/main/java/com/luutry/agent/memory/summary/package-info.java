/**
 * Memory记忆模块 - 会话摘要记忆包
 * <p>
 * 负责管理多轮对话的摘要记忆，包括：
 * - 对话内容压缩
 * - 关键信息提取
 * - 摘要生成与更新
 * - Token预算控制
 * </p>
 * <p>
 * 防止上下文token膨胀，保留核心对话信息
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.memory.summary;
