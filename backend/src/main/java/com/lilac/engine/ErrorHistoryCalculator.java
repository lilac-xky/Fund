package com.lilac.engine;

import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.engine.model.EstimateHistoryStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史误差统计器，算中位绝对误差、方向准确率、区间覆盖率与误差标准差
 */
@Slf4j
@Component
public class ErrorHistoryCalculator {

    private static final int SCALE = 6;
    private static final double DIRECTION_FLOOR = 0.05;

    /**
     * 统计最近 N 条误差记录，混合型没有点估计时改为用已记录的方向命中与区间结果
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
        int pointSamples = 0;
        int directionHit = 0;
        int directionTotal = 0;
        int intervalTotal = 0;
        int intervalHit = 0;
        double absSum = 0;
        List<Double> absErrors = new ArrayList<>(used.size());
        List<Double> widths = new ArrayList<>(used.size());
        double[] errors = new double[used.size()];
        double[] actuals = new double[used.size()];
        for (EstimateErrorVO row : used) {
            BigDecimal actual = row.getActualChange();
            if (actual == null) {
                continue;
            }
            samples++;
            actuals[samples - 1] = actual.doubleValue();
            if (row.getIntervalWidth() != null && row.getInInterval() != null) {
                intervalTotal++;
                widths.add(row.getIntervalWidth().doubleValue());
                if (Boolean.TRUE.equals(row.getInInterval())) {
                    intervalHit++;
                }
            }
            if (countsDirection(row)) {
                directionTotal++;
                if (Boolean.TRUE.equals(row.getDirectionHit())) {
                    directionHit++;
                }
            }
            BigDecimal estimated = row.getEstimatedChange();
            if (estimated == null) {
                continue;
            }
            double signed = estimated.subtract(actual).doubleValue();
            errors[pointSamples] = signed;
            absErrors.add(Math.abs(signed));
            absSum += Math.abs(signed);
            pointSamples++;
            if (countsDirectionBySign(row)) {
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
        if (pointSamples > 0) {
            stats.setMae(scale(absSum / pointSamples));
            stats.setMedianAbsError(scale(median(absErrors)));
            stats.setErrorSigma(stddev(errors, pointSamples));
            stats.setVolatility(stddev(actuals, samples));
        }
        stats.setDirectionAccuracy(directionTotal == 0 ? null : ratio(directionHit, directionTotal));
        stats.setIntervalCoverage(intervalTotal == 0 ? null : ratio(intervalHit, intervalTotal));
        fillIntervalWidth(stats, widths, sigmaFactor);
        return stats;
    }

    /**
     * 填入区间半宽与宽波动比，有实测区间宽度时用它，否则按误差标准差估
     *
     * @param stats       统计结果
     * @param widths      实测区间宽度
     * @param sigmaFactor 区间半宽使用的 sigma 倍数
     */
    private void fillIntervalWidth(EstimateHistoryStats stats, List<Double> widths, BigDecimal sigmaFactor) {
        BigDecimal halfWidth;
        if (!widths.isEmpty()) {
            halfWidth = scale(median(widths) / 2);
        } else if (stats.getErrorSigma() != null) {
            halfWidth = stats.getErrorSigma().multiply(sigmaFactor).setScale(SCALE, RoundingMode.HALF_UP);
        } else {
            return;
        }
        stats.setIntervalHalfWidth(halfWidth);
        if (stats.getVolatility() != null && stats.getVolatility().signum() > 0) {
            stats.setWidthRatio(halfWidth.multiply(BigDecimal.valueOf(2))
                    .divide(stats.getVolatility(), 4, RoundingMode.HALF_UP));
        }
    }

    /**
     * 这条记录是否已显式记录过方向命中，混合型走这条路
     *
     * @param row 误差记录
     * @return 有方向命中结论返回 true
     */
    private boolean countsDirection(EstimateErrorVO row) {
        return row.getDirectionHit() != null;
    }

    /**
     * 无点估计以外的记录才按涨跌同号判方向，零附近不参与
     *
     * @param row 误差记录
     * @return 参与方向判定返回 true
     */
    private boolean countsDirectionBySign(EstimateErrorVO row) {
        if (row.getDirectionHit() != null || row.getEstimatedChange() == null || row.getActualChange() == null) {
            return false;
        }
        BigDecimal floor = BigDecimal.valueOf(DIRECTION_FLOOR);
        return row.getEstimatedChange().abs().compareTo(floor) > 0
                && row.getActualChange().abs().compareTo(floor) > 0;
    }

    /**
     * 求中位数，偶数个样本取中间两个的平均
     *
     * @param values 序列
     * @return 中位数，空序列返回 0
     */
    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int size = sorted.length;
        return size % 2 == 1 ? sorted[size / 2] : (sorted[size / 2 - 1] + sorted[size / 2]) / 2;
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
     * 计算比例并保留四位小数
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比例
     */
    private BigDecimal ratio(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
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
}
