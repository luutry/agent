/**
 * 外部系统集成包
 * <p>
 * 负责与外部HR API的集成，包括：
 * - HrApiClient：外部HR系统API调用客户端
 * - Mock实现：开发/测试环境的模拟数据提供者
 * </p>
 * <p>
 * 所有外部依赖均通过接口隔离，Mock类命名MockXxx，
 * 使用@Profile("mock")或@ConditionalOnProperty装配。
 * 真实实现就绪后，只新增实现类+改装配，不改调用方。
 * </p>
 *
 * @author luutry
 * @since 1.0.0
 */
package com.luutry.agent.external;
