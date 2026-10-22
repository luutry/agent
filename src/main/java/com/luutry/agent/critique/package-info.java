/**
 * 反思校验层（Self-Critique）包
 * <p>
 * 负责对Agent输出进行校验，包括：
 * - 信息一致性校验
 * - RAG/Tool/Memory信息冲突检测
 * - 回答忠实度校验
 * - 幻觉内容识别
 * - 不确定性标注
 * </p>
 * <p>
 * 超出参考资料范围时拒绝编造内容
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.critique;
