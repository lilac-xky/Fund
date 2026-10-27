package com.lilac.adapter.quote;

import cn.hutool.json.JSONUtil;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.config.RedisKeys;
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
 * 当日行情缓存，按标的缓存短时间行情，避免同一标的被重复请求上游
 */
@Slf4j
@Component
public class QuoteCache {

    @Resource
    private RedissonClient redissonClient;

    @Value("${fund.adapter.quote.cache-ttl-seconds:300}")
    private long ttlSeconds;

    /**
     * 写入当日行情，Redis 不可用时只告警不阻断取数
     *
     * @param quote 行情
     */
    public void save(MarketQuote quote) {
        try {
            bucket(quote.getSymbol().symbol()).set(JSONUtil.toJsonStr(quote), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            // 缓存只是减少请求次数，写失败不能反过来让取数失败
            log.warn("行情缓存写入失败 {}：{}", quote.getSymbol().symbol(), e.getMessage());
        }
    }

    /**
     * 读取当日行情缓存
     *
     * @param symbol 标的完整代码
     * @return 行情，无缓存或 Redis 不可用时为 empty
     */
    public Optional<MarketQuote> read(String symbol) {
        try {
            String json = bucket(symbol).get();
            return json == null ? Optional.empty() : Optional.of(JSONUtil.toBean(json, MarketQuote.class));
        } catch (RuntimeException e) {
            log.warn("行情缓存读取失败 {}：{}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 取指定标的的缓存桶，key 用完整代码而非品种业务主键：同一标的的行情与品种类型无关
     *
     * @param symbol 标的完整代码
     * @return 缓存桶
     */
    private RBucket<String> bucket(String symbol) {
        return redissonClient.getBucket(RedisKeys.quoteKey(symbol), StringCodec.INSTANCE);
    }
}
