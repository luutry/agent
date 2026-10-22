/**
 * RAG检索模块 - Chunk切片包
 * <p>
 * 负责将文档切分为合适大小的文本块，包括：
 * - 固定长度切片
 * - 语义边界切片
 * - 重叠切片策略
 * - 动态Token预算切片
 * </p>
 * <p>
 * 保证切片质量，避免语义断裂
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.rag.chunker;
