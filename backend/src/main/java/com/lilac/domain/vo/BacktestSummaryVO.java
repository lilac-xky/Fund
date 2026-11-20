package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 回测里单只基金的汇总指标
 */
@Data
public class BacktestSummaryVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 参与统计的记录数
     */
    private Integer sampleCount;

    /**
     * 平均绝对误差，百分点
     */
    private BigDecimal mae;

    /**
     * 绝对误差中位数，百分点
     */
    private BigDecimal medianAbsError;

    /**
     * 绝对误差 P90，百分点
     */
    private BigDecimal p90AbsError;

    /**
     * 有符号偏差，预估均值减实际均值，百分点
     */
    private BigDecimal bias;

    /**
     * 超阈值记录数
     */
    private Integer overThresholdCount;

    /**
     * 方向命中率，0~1
     */
    private BigDecimal directionAccuracy;

    /**
     * 区间覆盖率，0~1
     */
    private BigDecimal intervalCoverage;

    private static final long serialVersionUID = 1L;
}
