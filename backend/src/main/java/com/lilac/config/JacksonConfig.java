package com.lilac.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化配置，把 Long 一律写成字符串，避免雪花主键在浏览器里丢精度
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 Long 转字符串的序列化器，18 位雪花 ID 超出 JS 安全整数范围，按数字下发会被改掉末尾
     *
     * @return Jackson 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.postConfigurer(mapper -> {
            // 用 postConfigurer 追加模块，避免覆盖 Spring Boot 已注册的 JavaTime 等模块
            SimpleModule module = new SimpleModule("longAsString");
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            mapper.registerModule(module);
        });
    }
}
