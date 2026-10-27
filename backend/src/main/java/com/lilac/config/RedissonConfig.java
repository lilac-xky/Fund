package com.lilac.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端配置，复用 spring.data.redis 的连接参数
 */
@Configuration
public class RedissonConfig {

    /**
     * 按 spring.data.redis 配置构建单机客户端，密码为空时不设鉴权
     *
     * @param properties Redis 连接配置
     * @return Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties properties,
                                         @Value("${fund.adapter.redis.pool-size:16}") int poolSize) {
        Config config = new Config();
        config.setLazyInitialization(true).useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setConnectionPoolSize(poolSize)
                .setConnectionMinimumIdleSize(2)
                .setTimeout(3000)
                .setConnectTimeout(3000)
                .setRetryAttempts(2);
        if (StringUtils.hasText(properties.getPassword())) {
            config.useSingleServer().setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }
}
