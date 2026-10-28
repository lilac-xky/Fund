package com.lilac.engine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 历史误差统计，档位与可信度判定只看这几个量
 */
@Data
public class EstimateHistoryStats {

    /**
     * 参与统计的样本数
     */
    private int sampleCount;

    /**
     * 平均绝对误差，百分点
     */
    private BigDecimal mae;

    /**
     * 方向准确率，0~1；无有效样本时为 null
     */
    private BigDecimal directionAccuracy;

    /**
     * 误差标准差，百分点
     */
    private BigDecimal errorSigma;

    /**
     * 区间半宽，等于 sigma 倍数乘误差标准差
     */
    private BigDecimal intervalHalfWidth;

    /**
     * 实际涨跌的标准差，即波动，百分点
     */
    private BigDecimal volatility;

    /**
     * 区间宽与波动之比，2 × 半宽 ÷ 波动
     */
    private BigDecimal widthRatio;

    /**
     * 构造无历史样本的统计
     *
     * @return 空统计
     */
    public static EstimateHistoryStats empty() {
        EstimateHistoryStats stats = new EstimateHistoryStats();
        stats.setSampleCount(0);
        return stats;
    }

    /**
     * 样本是否足够参与档位判定
     *
     * @param minimum 最少样本数
     * @return 足够返回 true
     */
    public boolean enough(int minimum) {
        return sampleCount >= minimum && mae != null;
    }
}
