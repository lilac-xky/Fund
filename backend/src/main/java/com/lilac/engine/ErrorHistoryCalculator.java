package com.lilac.engine;

import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.engine.model.EstimateHistoryStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 历史误差统计器，只统计已校准过的误差记录，算出 MAE、方向准确率与误差标准差
 */
@Slf4j
@Component
public class ErrorHistoryCalculator {

    private static final int SCALE = 6;
    private static final double DIRECTION_FLOOR = 0.05;

    /**
     * 统计最近 N 条误差记录
     *
     * @param history     误差记录，按交易日升序
     * @param window      窗口长度，小于等于 0 表示全量
     * @param sigmaFactor 区间半宽使用的 sigma 倍数
     * @return 统计结果
     */
    public EstimateHistoryStats summarize(List<EstimateErrorVO> history, int window, BigDecimal sigmaFactor) {
        if (history == null || history.isEmpty()) {
            return EstimateHistoryStats.empty();
        }
        List<EstimateErrorVO> used = window > 0 && history.size() > window
                ? history.subList(history.size() - window, history.size()) : history;
        int samples = 0;
        double absSum = 0;
        int directionHit = 0;
        int directionTotal = 0;
        double[] errors = new double[used.size()];
        double[] actuals = new double[used.size()];
        for (EstimateErrorVO row : used) {
            BigDecimal estimated = row.getEstimatedChange();
            BigDecimal actual = row.getActualChange();
            if (estimated == null || actual == null) {
                continue;
            }
            errors[samples] = estimated.subtract(actual).doubleValue();
            actuals[samples] = actual.doubleValue();
            samples++;
            absSum += Math.abs(errors[samples - 1]);
            // 零附近的白噪声不参与方向判定，避免把随机波动算成命中
            if (Math.abs(estimated.doubleValue()) > DIRECTION_FLOOR && Math.abs(actual.doubleValue()) > DIRECTION_FLOOR) {
                directionTotal++;
                if (estimated.signum() == actual.signum()) {
                    directionHit++;
                }
            }
        }
        if (samples == 0) {
            return EstimateHistoryStats.empty();
        }
        EstimateHistoryStats stats = new EstimateHistoryStats();
        stats.setSampleCount(samples);
        stats.setMae(scale(absSum / samples));
        stats.setErrorSigma(stddev(errors, samples));
        stats.setVolatility(stddev(actuals, samples));
        stats.setDirectionAccuracy(directionTotal == 0 ? null
                : BigDecimal.valueOf(directionHit).divide(BigDecimal.valueOf(directionTotal), 4, RoundingMode.HALF_UP));
        BigDecimal halfWidth = stats.getErrorSigma() == null ? null
                : stats.getErrorSigma().multiply(sigmaFactor).setScale(SCALE, RoundingMode.HALF_UP);
        stats.setIntervalHalfWidth(halfWidth);
        if (halfWidth != null && stats.getVolatility() != null && stats.getVolatility().signum() > 0) {
            stats.setWidthRatio(halfWidth.multiply(BigDecimal.valueOf(2))
                    .divide(stats.getVolatility(), 4, RoundingMode.HALF_UP));
        }
        return stats;
    }

    /**
     * 计算标准差
     *
     * @param values 序列
     * @param count  有效个数
     * @return 标准差，不足两个样本返回 null
     */
    private BigDecimal stddev(double[] values, int count) {
        if (count < 2) {
            return null;
        }
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        double mean = sum / count;
        double squares = 0;
        for (int i = 0; i < count; i++) {
            squares += (values[i] - mean) * (values[i] - mean);
        }
        return scale(Math.sqrt(squares / count));
    }

    /**
     * 统一保留六位小数
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 统一保留六位小数，入参已是 BigDecimal
     *
     * @param value 原始值
     * @return 保留后的值，入参为 null 时返回 null
     */
    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
