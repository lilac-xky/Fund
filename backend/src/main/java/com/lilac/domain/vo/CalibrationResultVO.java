package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 晚间校准汇总，逐只对照与待校准缺口分两组，供复盘页直接呈现
 */
@Data
public class CalibrationResultVO implements Serializable {

    /**
     * 校准交易日
     */
    private LocalDate tradeDate;

    /**
     * 逐只对照结果
     */
    private List<CalibrationItemVO> items;

    /**
     * 超阈值的基金，从 items 中筛出便于页面直接标红
     */
    private List<CalibrationItemVO> overThresholdItems;

    /**
     * 建议停用的基金
     */
    private List<CalibrationItemVO> suspensionHints;

    /**
     * 未能校准的基金，如当日预估缺失或净值尚未公布
     */
    private List<CalibrationGapVO> gaps;

    /**
     * 校准只数
     */
    private Integer calibratedCount;

    /**
     * 超阈值只数
     */
    private Integer overThresholdCount;

    /**
     * 未能校准只数
     */
    private Integer gapCount;

    /**
     * 净利润落库条数，含覆盖写
     */
    private Integer writtenCount;

    /**
     * 校准前主动刷新成功的净值只数，用于区分「没公布」与「没抓到」
     */
    private Integer netWorthRefreshed;

    /**
     * 本次说明，如「当日预估缺失已跳过 2 只」
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}
