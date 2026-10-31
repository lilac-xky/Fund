package com.lilac.engine;

import cn.hutool.json.JSONUtil;
import com.lilac.config.RedisKeys;
import com.lilac.strategy.model.IndicatorResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 技术指标结果缓存，避免每次请求都重算整段日线，按标的与参数指纹区分
 */
@Slf4j
@Component
public class IndicatorCache {

    @Resource
    private RedissonClient redissonClient;

    @Value("${fund.indicator.cache-ttl-seconds:3600}")
    private long ttlSeconds;

    /**
     * 写入指标结果，Redis 不可用时只告警不阻断
     *
     * @param symbol    标的完整代码
     * @param signature 参数指纹，参数改了就不该命中旧结果
     * @param result    指标结果
     */
    public void save(String symbol, String signature, IndicatorResult result) {
        if (symbol == null || signature == null || result == null) {
            return;
        }
        try {
            bucket(symbol, signature).set(JSONUtil.toJsonStr(result), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            // 缓存只是省一次重算，写失败不能反过来让指标计算失败
            log.warn("指标缓存写入失败 {}：{}", symbol, e.getMessage());
        }
    }

    /**
     * 读取指标结果缓存
     *
     * @param symbol    标的完整代码
     * @param signature 参数指纹
     * @return 指标结果，无缓存或 Redis 不可用时为空
     */
    public Optional<IndicatorResult> read(String symbol, String signature) {
        if (symbol == null || signature == null) {
            return Optional.empty();
        }
        try {
            String json = bucket(symbol, signature).get();
            return json == null ? Optional.empty()
                    : Optional.of(JSONUtil.toBean(json, IndicatorResult.class));
        } catch (RuntimeException e) {
            log.warn("指标缓存读取失败 {}：{}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 取缓存桶，参数指纹进 key，改参数后自然失效，不必手工清理
     *
     * @param symbol    标的完整代码
     * @param signature 参数指纹
     * @return 缓存桶
     */
    private RBucket<String> bucket(String symbol, String signature) {
        return redissonClient.getBucket(RedisKeys.indicatorKey(symbol, signature), StringCodec.INSTANCE);
    }
}
