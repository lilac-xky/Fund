package com.lilac.engine;

import com.lilac.engine.model.EstimateCalibrationVerdict;
import com.lilac.enums.ChangeDirection;
import com.lilac.enums.FundCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 晚间校准判定器，非混合型按类型阈值判点估计误差，混合型只判方向命中与区间覆盖
 */
@Slf4j
@Component
public class CalibrationJudge {

    private static final int SCALE = 6;

    /**
     * 判定一条校准记录，混合型不参与点估计阈值判定
     *
     * @param category   基金类型
     * @param direction  当时给出的方向，可为 null
     * @param estimated  当时点估计，百分点；B、C 档为 null
     * @param actual     当日实际涨跌幅，百分点
     * @param low        当时区间下限，百分点
     * @param high       当时区间上限，百分点
     * @param thresholds 阈值配置
     * @return 判定结论
     */
    public EstimateCalibrationVerdict judge(FundCategory category, ChangeDirection direction, BigDecimal estimated,
                                            BigDecimal actual, BigDecimal low, BigDecimal high,
                                            EstimateThresholds thresholds) {
        EstimateCalibrationVerdict verdict = new EstimateCalibrationVerdict();
        verdict.setActualChange(scale(actual));
        if (category == FundCategory.MIXED) {
            judgeMixed(verdict, direction, estimated, actual, low, high, thresholds);
            return verdict;
        }
        judgePoint(verdict, category, estimated, actual, thresholds);
        return verdict;
    }

    /**
     * 混合型判定方向是否命中、实际值是否落在区间内，并记录区间宽度
     *
     * @param verdict    结论
     * @param direction  当时给出的方向
     * @param estimated  点估计，可为 null
     * @param actual     实际涨跌幅
     * @param low        区间下限
     * @param high       区间上限
     * @param thresholds 阈值配置
     */
    private void judgeMixed(EstimateCalibrationVerdict verdict, ChangeDirection direction, BigDecimal estimated,
                            BigDecimal actual, BigDecimal low, BigDecimal high, EstimateThresholds thresholds) {
        if (estimated != null) {
            verdict.setEstimatedChange(scale(estimated));
            verdict.setErrorValue(scale(estimated.subtract(actual)));
        }
        verdict.setDirectionHit(directionHit(direction, actual, thresholds));
        if (low != null && high != null) {
            verdict.setIntervalWidth(scale(high.subtract(low)));
            verdict.setInInterval(actual.compareTo(low) >= 0 && actual.compareTo(high) <= 0);
        }
        if (verdict.getInInterval() == null) {
            // 没有区间可对照时只能看方向，方向没命中即视为不合格
            verdict.setOverThreshold(!Boolean.TRUE.equals(verdict.getDirectionHit()));
            return;
        }
        verdict.setOverThreshold(!Boolean.TRUE.equals(verdict.getDirectionHit()) || !verdict.getInInterval());
    }

    /**
     * 非混合型按类型阈值判定点估计误差，债券单独用更紧的阈值
     *
     * @param verdict    结论
     * @param category   基金类型
     * @param estimated  预估涨跌幅
     * @param actual     实际涨跌幅
     * @param thresholds 阈值配置
     */
    private void judgePoint(EstimateCalibrationVerdict verdict, FundCategory category, BigDecimal estimated,
                            BigDecimal actual, EstimateThresholds thresholds) {
        BigDecimal threshold = category == FundCategory.BOND
                ? thresholds.getBondErrorThreshold() : thresholds.getStockErrorThreshold();
        verdict.setThresholdValue(threshold);
        if (estimated == null) {
            // 非混合型本应给数字却没给，说明当时就不可预估，这一天无从对照误差
            verdict.setOverThreshold(Boolean.TRUE);
            return;
        }
        BigDecimal error = estimated.subtract(actual);
        verdict.setEstimatedChange(scale(estimated));
        verdict.setErrorValue(scale(error));
        verdict.setOverThreshold(error.abs().compareTo(threshold) > 0);
    }

    /**
     * 比较当时给出的方向与实际涨跌方向，两侧都靠近零时视为横盘命中
     *
     * @param direction  当时给出的方向
     * @param actual     实际涨跌幅
     * @param thresholds 阈值配置
     * @return 命中返回 true，无方向可判返回 null
     */
    private Boolean directionHit(ChangeDirection direction, BigDecimal actual, EstimateThresholds thresholds) {
        if (direction == null || actual == null) {
            return null;
        }
        return direction == directionOf(actual, thresholds.getDirectionFloor());
    }

    /**
     * 涨跌幅转方向，绝对值小于阈值下限按横盘
     *
     * @param change 涨跌幅，百分点
     * @param floor  横盘下限
     * @return 方向
     */
    public ChangeDirection directionOf(BigDecimal change, BigDecimal floor) {
        if (change == null) {
            return null;
        }
        if (change.abs().compareTo(floor) < 0) {
            return ChangeDirection.FLAT;
        }
        return change.signum() > 0 ? ChangeDirection.UP : ChangeDirection.DOWN;
    }

    /**
     * 统一保留六位小数
     *
     * @param value 原始值
     * @return 保留后的值，入参为 null 时返回 null
     */
    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
