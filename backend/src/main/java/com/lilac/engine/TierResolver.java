package com.lilac.engine;

import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 输出档位判定器，按历史实测误差定档，并用长短两个窗口交叉校验避免随单日误差跳变
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
        EstimateTier longTier = judge(stats, thresholds);
        EstimateTier shortTier = recentStats != null && recentStats.enough(thresholds.getShortWindow())
                ? judge(recentStats, thresholds) : longTier;
        if (shortTier == longTier) {
            return longTier;
        }
        // 长短窗口结论不一致说明处在噪声区，保持原档位不动，避免按日跳变
        log.info("档位判定两窗口不一致 长={} 短={}，沿用原档位={}", longTier, shortTier, previousTier);
        return previousTier == null ? longTier : previousTier;
    }

    /**
     * 按 MAE、方向准确率与区间宽波动比三件事判定档位
     *
     * @param stats      误差统计
     * @param thresholds 阈值配置
     * @return 档位
     */
    private EstimateTier judge(EstimateHistoryStats stats, EstimateThresholds thresholds) {
        if (stats.getMae() != null && stats.getMae().compareTo(thresholds.getAMaeLimit()) <= 0) {
            return EstimateTier.A;
        }
        if (meetsB(stats, thresholds)) {
            return EstimateTier.B;
        }
        return EstimateTier.C;
    }

    /**
     * B 档要求方向准确率达标且区间不能比自身波动还宽
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
        return accuracy.compareTo(thresholds.getBDirectionAccuracy()) >= 0
                && ratio.compareTo(thresholds.getBWidthRatio()) < 0;
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
