package com.lilac.engine;

import cn.hutool.json.JSONUtil;
import com.lilac.domain.vo.EstimatePointVO;
import com.lilac.engine.model.EstimateTrack;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 今日预估的盘中轨迹，每次预估成功后记一个点，页面据此画出当天走势
 */
@Slf4j
@Component
public class EstimateTrackStore {

    private static final String TRACK_PREFIX = "fund:estimate:track:";
    private static final int SCALE = 4;
    /** 采样最小间隔，分钟；过于密集的点画出来是一条噪声，也白占缓存 */
    private static final int MIN_INTERVAL_MINUTES = 2;
    /** 单日最多保留的点数，一天交易时段按 5 分钟一次约 60 个，留一倍余量 */
    private static final int MAX_POINTS = 240;

    @Value("${fund.estimate.track-ttl-seconds:86400}")
    private long ttlSeconds;

    private final RedissonClient redissonClient;

    /**
     * 注入 Redis 客户端
     *
     * @param redissonClient Redis 客户端
     */
    public EstimateTrackStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 记录一个采样点，与上一个点间隔过近或值未变时跳过，Redis 不可用只告警
     *
     * @param tradeDate      行情所属交易日
     * @param overallChange  整体预估涨跌幅，百分点
     * @param estimableCount 可预估只数
     */
    public void record(LocalDate tradeDate, BigDecimal overallChange, Integer estimableCount) {
        if (tradeDate == null) {
            return;
        }
        try {
            EstimateTrack track = read(tradeDate);
            LocalDateTime now = LocalDateTime.now();
            if (!shouldAppend(track, overallChange, now)) {
                return;
            }
            EstimatePointVO point = new EstimatePointVO();
            point.setTime(now);
            point.setChange(overallChange == null ? null
                    : overallChange.setScale(SCALE, RoundingMode.HALF_UP));
            point.setEstimableCount(estimableCount);
            track.getPoints().add(point);
            if (track.getPoints().size() > MAX_POINTS) {
                track.getPoints().remove(0);
            }
            track.setTradeDate(tradeDate);
            write(tradeDate, track);
        } catch (RuntimeException e) {
            // 轨迹只用于画图，记不下不能反过来让预估失败
            log.warn("预估轨迹写入失败 {}：{}", tradeDate, e.getMessage());
        }
    }

    /**
     * 读取某个交易日的轨迹，无记录时返回空轨迹
     *
     * @param tradeDate 交易日
     * @return 轨迹，按时间升序
     */
    public EstimateTrack read(LocalDate tradeDate) {
        EstimateTrack empty = new EstimateTrack();
        empty.setTradeDate(tradeDate);
        empty.setPoints(new ArrayList<>());
        if (tradeDate == null) {
            return empty;
        }
        try {
            String json = bucket(tradeDate).get();
            if (json == null || json.isBlank()) {
                return empty;
            }
            EstimateTrack track = JSONUtil.toBean(json, EstimateTrack.class);
            if (track.getPoints() == null) {
                track.setPoints(new ArrayList<>());
            }
            return track;
        } catch (RuntimeException e) {
            log.warn("预估轨迹读取失败 {}：{}", tradeDate, e.getMessage());
            return empty;
        }
    }

    /**
     * 判断是否要新增采样点：首个点必记，之后要求间隔够且数值有变化
     *
     * @param track  已有轨迹
     * @param change 本次整体预估
     * @param now    当前时间
     * @return 需要记录返回 true
     */
    private boolean shouldAppend(EstimateTrack track, BigDecimal change, LocalDateTime now) {
        if (track.getPoints() == null || track.getPoints().isEmpty()) {
            return true;
        }
        EstimatePointVO last = track.getPoints().get(track.getPoints().size() - 1);
        if (last.getTime() != null && last.getTime().plusMinutes(MIN_INTERVAL_MINUTES).isAfter(now)) {
            return false;
        }
        BigDecimal lastChange = last.getChange();
        if (change == null && lastChange == null) {
            return false;
        }
        return change == null || lastChange == null || change.compareTo(lastChange) != 0;
    }

    /**
     * 写入轨迹
     *
     * @param tradeDate 交易日
     * @param track     轨迹
     */
    private void write(LocalDate tradeDate, EstimateTrack track) {
        bucket(tradeDate).set(JSONUtil.toJsonStr(track), Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 取指定交易日的轨迹缓存桶
     *
     * @param tradeDate 交易日
     * @return 缓存桶
     */
    private RBucket<String> bucket(LocalDate tradeDate) {
        return redissonClient.getBucket(TRACK_PREFIX + tradeDate, StringCodec.INSTANCE);
    }

    /**
     * 供排障读取轨迹点数量
     *
     * @param tradeDate 交易日
     * @return 点数
     */
    public int size(LocalDate tradeDate) {
        List<EstimatePointVO> points = read(tradeDate).getPoints();
        return points == null ? 0 : points.size();
    }
}
