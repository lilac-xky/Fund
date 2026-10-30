package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.service.EstimateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 盘中预估采样，交易时段内每 5 分钟重算一次整体预估，让行情条的曲线当天就有形状
 */
@Slf4j
@Component
public class IntradayTrackSampler {

    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 5);

    @Resource
    private EstimateService estimateService;

    @Resource
    private TradingCalendar tradingCalendar;

    @Resource
    private TaskProperties taskProperties;

    /**
     * 交易时段内每 5 分钟重算一次预估并记一个采样点，只在曲线需要时工作
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Shanghai")
    public void sample() {
        if (!taskProperties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of(taskProperties.getZone()));
        if (!inTradingWindow(now) || !tradingCalendar.isTradingDay(now.toLocalDate())) {
            return;
        }
        try {
            // 强制重算才是新价格算出的新点，命中缓存只会把同一个数写两遍
            EstimateBatchRequest request = new EstimateBatchRequest();
            request.setRefresh(Boolean.TRUE);
            EstimateBatchVO result = estimateService.estimateBatch(request);
            log.info("盘中预估采样完成 时间={} 整体预估={} 可预估 {} 只", now.toLocalTime(),
                    result.getOverallChange(), result.getEstimableCount());
        } catch (RuntimeException e) {
            // 采样失败不能影响其它定时任务，曲线少一个点而已
            log.warn("盘中预估采样失败 时间={}：{}", now.toLocalTime(), e.getMessage());
        }
    }

    /**
     * 判断是否落在 A 股交易时段，午休与收盘后不采样
     *
     * @param now 当前时间
     * @return 交易时段内返回 true
     */
    private boolean inTradingWindow(LocalDateTime now) {
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        boolean morning = !time.isBefore(MORNING_OPEN) && !time.isAfter(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN) && !time.isAfter(AFTERNOON_CLOSE);
        return morning || afternoon;
    }

    /**
     * 供页面与排障查询当前是否在采样窗口内
     *
     * @return 窗口内返回 true
     */
    public boolean sampling() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(taskProperties.getZone()));
        return taskProperties.isEnabled() && inTradingWindow(now)
                && tradingCalendar.isTradingDay(now.toLocalDate());
    }
}
