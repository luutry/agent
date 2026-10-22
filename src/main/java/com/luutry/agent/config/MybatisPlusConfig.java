package com.luutry.agent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。扫描 repository.mapper 包下的 Mapper 接口。
 */
@Configuration
@MapperScan("com.luutry.agent.repository.mapper")
public class MybatisPlusConfig {
}
