package com.lilac.engine;

import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 输出档位判定器，按历史实测误差的中位数定档，并用长短两个窗口交叉校验避免随单日误差跳变
 */
@Slf4j
@Component
public class TierResolver {

    /**
     * 判定档位，历史不足时按类型给默认档
     *
     * @param category     基金类型
     * @param stats        长窗口误差统计，窗口长度取配置
     * @param recentStats  短窗口误差统计，用于交叉校验
     * @param previousTier 上一次记录的档位，可为 null
     * @param thresholds   阈值配置
     * @return 档位
     */
    public EstimateTier resolve(FundCategory category, EstimateHistoryStats stats, EstimateHistoryStats recentStats,
                                EstimateTier previousTier, EstimateThresholds thresholds) {
        if (stats == null || !stats.enough(thresholds.getMinSamples())) {
            // 历史不足时不能凭空定档，按类型的固有精度给默认值
            return defaultTier(category);
        }
        EstimateTier longTier = judge(category, stats, thresholds);
        EstimateTier shortTier = recentStats != null && recentStats.enough(thresholds.getShortWindow())
                ? judge(category, recentStats, thresholds) : longTier;
        if (shortTier == longTier) {
            return longTier;
        }
        // 长短窗口结论不一致说明处在噪声区，保持原档位不动，避免按日跳变
        log.info("档位判定两窗口不一致 长={} 短={}，沿用原档位={}", longTier, shortTier, previousTier);
        return previousTier == null ? longTier : previousTier;
    }

    /**
     * 按中位绝对误差、方向准确率与区间宽波动比三件事判定档位，混合型不允许判 A 档
     *
     * @param category   基金类型
     * @param stats      误差统计
     * @param thresholds 阈值配置
     * @return 档位
     */
    private EstimateTier judge(FundCategory category, EstimateHistoryStats stats, EstimateThresholds thresholds) {
        // 混合型不做点估计承诺，历史再好也只是 B 档，避免出现单日数字
        if (category != FundCategory.MIXED && pointEstimateOk(stats, thresholds)) {
            return EstimateTier.A;
        }
        if (meetsB(stats, thresholds)) {
            return EstimateTier.B;
        }
        return EstimateTier.C;
    }

    /**
     * 点估计是否达标，中位绝对误差不超上限且区间覆盖率也够
     *
     * @param stats      误差统计
     * @param thresholds 阈值配置
     * @return 达标返回 true
     */
    private boolean pointEstimateOk(EstimateHistoryStats stats, EstimateThresholds thresholds) {
        BigDecimal mae = stats.getMedianAbsError() == null ? stats.getMae() : stats.getMedianAbsError();
        if (mae == null || mae.compareTo(thresholds.getAMaeLimit()) > 0) {
            return false;
        }
        BigDecimal coverage = stats.getIntervalCoverage();
        return coverage == null || coverage.compareTo(thresholds.getIntervalCoverageMin()) >= 0;
    }

    /**
     * B 档要求方向准确率达标、区间不宽于自身波动，且区间覆盖率够
     *
     * @param stats      误差统计
     * @param thresholds 阈值配置
     * @return 满足返回 true
     */
    private boolean meetsB(EstimateHistoryStats stats, EstimateThresholds thresholds) {
        BigDecimal accuracy = stats.getDirectionAccuracy();
        BigDecimal ratio = stats.getWidthRatio();
        if (accuracy == null || ratio == null) {
            return false;
        }
        BigDecimal coverage = stats.getIntervalCoverage();
        return accuracy.compareTo(thresholds.getBDirectionAccuracy()) >= 0
                && ratio.compareTo(thresholds.getBWidthRatio()) < 0
                && (coverage == null || coverage.compareTo(thresholds.getIntervalCoverageMin()) >= 0);
    }

    /**
     * 无误差历史时的默认档，混合型按 PRD 只给方向与区间，其余类型给点估计
     *
     * @param category 基金类型
     * @return 默认档位
     */
    private EstimateTier defaultTier(FundCategory category) {
        return category == FundCategory.MIXED ? EstimateTier.B : EstimateTier.A;
    }
}
