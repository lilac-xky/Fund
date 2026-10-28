package com.lilac.engine;

import cn.hutool.json.JSONUtil;
import com.lilac.config.RedisKeys;
import com.lilac.domain.vo.EstimateBatchVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 预估结果缓存，供页面与邮件共用同一份当日结果，并带出数据日期
 */
@Slf4j
@Component
public class EstimateCache {

    @Resource
    private RedissonClient redissonClient;

    @Value("${fund.estimate.cache-ttl-seconds:1800}")
    private long ttlSeconds;

    /**
     * 写入当日预估结果，Redis 不可用时只告警不阻断
     *
     * @param result    预估总览
     * @param tradeDate 数据日期
     */
    public void save(EstimateBatchVO result, LocalDate tradeDate) {
        if (result == null || tradeDate == null) {
            return;
        }
        try {
            bucket(tradeDate).set(JSONUtil.toJsonStr(result), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            // 缓存只是省一次重算，写失败不能让预估本身失败
            log.warn("预估缓存写入失败 {}：{}", tradeDate, e.getMessage());
        }
    }

    /**
     * 读取指定数据日期的预估结果
     *
     * @param tradeDate 数据日期
     * @return 预估总览，无缓存或 Redis 不可用时为空
     */
    public Optional<EstimateBatchVO> read(LocalDate tradeDate) {
        if (tradeDate == null) {
            return Optional.empty();
        }
        try {
            String json = bucket(tradeDate).get();
            return json == null ? Optional.empty() : Optional.of(JSONUtil.toBean(json, EstimateBatchVO.class));
        } catch (RuntimeException e) {
            log.warn("预估缓存读取失败 {}：{}", tradeDate, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 清掉指定日期的缓存，供强制刷新使用
     *
     * @param tradeDate 数据日期
     */
    public void evict(LocalDate tradeDate) {
        try {
            bucket(tradeDate).delete();
        } catch (RuntimeException e) {
            log.warn("预估缓存清理失败 {}：{}", tradeDate, e.getMessage());
        }
    }

    /**
     * 取指定日期的缓存桶，key 按日期区分，跨日不串数据
     *
     * @param tradeDate 数据日期
     * @return 缓存桶
     */
    private RBucket<String> bucket(LocalDate tradeDate) {
        return redissonClient.getBucket(RedisKeys.estimateKey(tradeDate.toString()), StringCodec.INSTANCE);
    }
}
