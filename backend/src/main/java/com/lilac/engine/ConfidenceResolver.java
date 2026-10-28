package com.lilac.engine;

import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.enums.EstimateConfidence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 可信度判定器，只看持仓覆盖率与历史误差，不使用披露距今月数
 */
@Component
public class ConfidenceResolver {

    /**
     * 判定可信度
     *
     * @param coverage   参与加权的覆盖率，百分点；指数直取等无持仓分支传 100
     * @param stats      历史误差统计，可为 null 表示无历史
     * @param degraded   是否为降级缓存数据
     * @param thresholds 阈值配置
     * @return 可信度
     */
    public EstimateConfidence resolve(BigDecimal coverage, EstimateHistoryStats stats, boolean degraded,
                                      EstimateThresholds thresholds) {
        if (degraded) {
            return EstimateConfidence.LOW;
        }
        BigDecimal effectiveCoverage = coverage == null ? BigDecimal.valueOf(100) : coverage;
        BigDecimal mae = stats == null ? null : stats.getMae();
        boolean highMae = mae == null || mae.compareTo(thresholds.getHighMae()) <= 0;
        boolean mediumMae = mae == null || mae.compareTo(thresholds.getMediumMae()) <= 0;
        if (effectiveCoverage.compareTo(thresholds.getHighCoverage()) >= 0 && highMae) {
            return EstimateConfidence.HIGH;
        }
        if (effectiveCoverage.compareTo(thresholds.getMediumCoverage()) >= 0 && mediumMae) {
            return EstimateConfidence.MEDIUM;
        }
        return EstimateConfidence.LOW;
    }
}
