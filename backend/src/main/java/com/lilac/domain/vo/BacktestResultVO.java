package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 因子回测结果，含逐只汇总、整体统计与本次生效的参数回显
 */
@Data
public class BacktestResultVO implements Serializable {

    /**
     * 回测起始日
     */
    private LocalDate startDate;

    /**
     * 回测截止日
     */
    private LocalDate endDate;

    /**
     * 实际参与回测的交易日数
     */
    private Integer tradeDateCount;

    /**
     * 参与回测的基金数
     */
    private Integer varietyCount;

    /**
     * 产出可对照结论的记录数
     */
    private Integer sampleCount;

    /**
     * 整体平均绝对误差，百分点
     */
    private BigDecimal mae;

    /**
     * 整体绝对误差中位数，百分点
     */
    private BigDecimal medianAbsError;

    /**
     * 整体绝对误差 P90，百分点
     */
    private BigDecimal p90AbsError;

    /**
     * 超阈值记录占比，0~1
     */
    private BigDecimal overThresholdRatio;

    /**
     * 方向命中率，0~1
     */
    private BigDecimal directionAccuracy;

    /**
     * 区间覆盖率，0~1
     */
    private BigDecimal intervalCoverage;

    /**
     * 逐只汇总
     */
    private List<BacktestSummaryVO> summaries;

    /**
     * 本次生效的参数回显，保证结果可复现
     */
    private BacktestParamVO params;

    /**
     * 逐条明细
     */
    private List<BacktestItemVO> items;

    /**
     * 说明，如样本不足、缺口原因
     */
    private List<String> notes;

    private static final long serialVersionUID = 1L;
}
