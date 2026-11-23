package com.lilac.init;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 历史区间中心回填，把旧留存里能用上下界反推的中心值补进两张表，避免残差样本从零开始
 */
@Slf4j
@Component
@Order(2)
public class SnapshotCenterBackfiller implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 两张表各按自己的上下界回填中心值，再让误差表与留存表对齐
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        backfillFromBounds("estimate_snapshot");
        backfillFromBounds("estimate_error");
        syncErrorFromSnapshot();
    }

    /**
     * 按本表自己的区间上下界回填中心值，只补空值不覆盖已有结论
     *
     * @param table 表名
     */
    private void backfillFromBounds(String table) {
        try {
            // 中心值 = (下限 + 上限) ÷ 2，旧留存只有上下界，这是唯一可反推的途径
            int updated = jdbcTemplate.update("UPDATE " + table
                    + " SET centerChange = (intervalLow + intervalHigh) / 2"
                    + " WHERE centerChange IS NULL AND intervalLow IS NOT NULL AND intervalHigh IS NOT NULL");
            if (updated > 0) {
                log.info("{} 历史区间中心已回填 {} 条", table, updated);
            }
        } catch (RuntimeException e) {
            // 回填只是让残差样本早点够，失败不该阻断启动
            log.warn("{} 历史区间中心回填失败：{}", table, e.getMessage());
        }
    }

    /**
     * 误差表按留存表对齐中心值，两表口径一致才不会让残差统计分叉
     */
    private void syncErrorFromSnapshot() {
        try {
            int linked = jdbcTemplate.update("UPDATE estimate_error e"
                    + " JOIN estimate_snapshot s ON s.varietyKey = e.varietyKey AND s.tradeDate = e.tradeDate"
                    + " SET e.centerChange = s.centerChange"
                    + " WHERE e.centerChange IS NULL AND s.centerChange IS NOT NULL");
            if (linked > 0) {
                log.info("estimate_error 从留存表补齐中心值 {} 条", linked);
            }
        } catch (RuntimeException e) {
            log.warn("estimate_error 从留存表补齐中心值失败：{}", e.getMessage());
        }
    }
}
