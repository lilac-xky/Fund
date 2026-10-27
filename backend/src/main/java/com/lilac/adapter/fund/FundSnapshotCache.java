package com.lilac.adapter.fund;

import cn.hutool.json.JSONUtil;
import com.lilac.adapter.model.FundSnapshot;
import com.lilac.config.RedisKeys;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 净值与持仓快照的降级缓存，只存最近一次成功结果
 */
@Slf4j
@Component
public class FundSnapshotCache {

    private static final String SOURCE = "eastmoney";
    private static final Duration TTL = Duration.ofSeconds(RedisKeys.FALLBACK_TTL_SECONDS);

    @Resource
    private RedissonClient redissonClient;

    /**
     * 写入最近一次成功快照，Redis 不可用时只告警不阻断取数
     *
     * @param snapshot 快照
     */
    public void save(FundSnapshot snapshot) {
        try {
            RBucket<String> bucket = bucket(snapshot.getCode());
            bucket.set(JSONUtil.toJsonStr(snapshot), TTL);
        } catch (RuntimeException e) {
            // 缓存是降级手段，写失败不能反过来让正常取数失败
            log.warn("快照写入失败 code={}：{}", snapshot.getCode(), e.getMessage());
        }
    }

    /**
     * 读取最近一次成功快照
     *
     * @param code 6 位基金代码
     * @return 快照，无缓存或 Redis 不可用时为空
     */
    public Optional<FundSnapshot> read(String code) {
        try {
            String json = bucket(code).get();
            return json == null ? Optional.empty() : Optional.of(JSONUtil.toBean(json, FundSnapshot.class));
        } catch (RuntimeException e) {
            log.warn("快照读取失败 code={}：{}", code, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 取指定代码的缓存桶
     *
     * @param code 6 位基金代码
     * @return 缓存桶
     */
    private RBucket<String> bucket(String code) {
        return redissonClient.getBucket(RedisKeys.fallbackKey(SOURCE, code), StringCodec.INSTANCE);
    }
}
