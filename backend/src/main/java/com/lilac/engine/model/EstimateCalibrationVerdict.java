package com.lilac.engine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 一条晚间校准的判定结论，非混合型只填误差，混合型填方向与区间对照
 */
@Data
public class EstimateCalibrationVerdict {

    /**
     * 参与对照的预估值，百分点；无点估计时为 null
     */
    private BigDecimal estimatedChange;

    /**
     * 当日实际涨跌幅，百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际，百分点
     */
    private BigDecimal errorValue;

    /**
     * 本次判定所用阈值，百分点；混合型为 null
     */
    private BigDecimal thresholdValue;

    /**
     * 方向是否命中，混合型统计用
     */
    private Boolean directionHit;

    /**
     * 实际值是否落在所给区间内，混合型统计用
     */
    private Boolean inInterval;

    /**
     * 所给区间宽度，百分点
     */
    private BigDecimal intervalWidth;

    /**
     * 是否超阈值，混合型为方向未命中或落在区间外
     */
    private Boolean overThreshold;
}
