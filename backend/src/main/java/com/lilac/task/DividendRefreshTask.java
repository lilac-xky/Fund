package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.domain.vo.IndexDividendRefreshVO;
import com.lilac.service.IndexDividendService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 股息率定时刷新，红利加减仓依赖指数股息率，超期就取不到数，故每天开盘前自动抓一次
 */
@Slf4j
@Component
public class DividendRefreshTask {

    @Resource
    private IndexDividendService indexDividendService;

    @Resource
    private TradingCalendar tradingCalendar;

    @Resource
    private TaskProperties taskProperties;

    /**
     * 刷新全部红利基金跟踪指数的股息率，只刷不判断，取不到的仍走手工覆盖
     */
    public void refresh() {
        if (!taskProperties.isEnabled() || !taskProperties.isSchedulerEnabled()) {
            return;
        }
        if (!tradingCalendar.isTradingDay(java.time.LocalDate.now())) {
            return;
        }
        try {
            // 留空表示按库内红利基金的跟踪指数推导目标，不在代码里写死指数清单
            IndexDividendRefreshVO result = indexDividendService.refresh(null);
            int targets = result.getItems() == null ? 0 : result.getItems().size();
            log.info("股息率定时刷新完成 目标 {} 只 写入 {} 只 需手工 {} 只",
                    targets, result.getSavedCount(), result.getManualRequiredCount());
        } catch (RuntimeException e) {
            // 刷新失败不影响手工录入与已有数据，过期的仍按过期处理
            log.warn("股息率定时刷新失败：{}", e.getMessage());
        }
    }
}
