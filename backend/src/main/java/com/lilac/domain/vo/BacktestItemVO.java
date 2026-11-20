package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回测的逐日逐只结果，一条对应某基金某交易日的重放结论
 */
@Data
public class BacktestItemVO implements Serializable {

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
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 当时走的分支中文名
     */
    private String branchLabel;

    /**
     * 重放得到的输出档位
     */
    private String outputTier;

    /**
     * 重放得到的预估涨跌幅，百分点；B、C 档为空
     */
    private BigDecimal estimatedChange;

    /**
     * 重放得到的涨跌方向
     */
    private String direction;

    /**
     * 重放得到的区间下限，百分点
     */
    private BigDecimal intervalLow;

    /**
     * 重放得到的区间上限，百分点
     */
    private BigDecimal intervalHigh;

    /**
     * 实际涨跌幅，百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际，百分点
     */
    private BigDecimal errorValue;

    /**
     * 本次使用的阈值，百分点
     */
    private BigDecimal thresholdValue;

    /**
     * 是否超阈值
     */
    private Boolean overThreshold;

    /**
     * 方向是否命中
     */
    private Boolean directionHit;

    /**
     * 实际值是否落在区间内
     */
    private Boolean inInterval;

    private static final long serialVersionUID = 1L;
}
