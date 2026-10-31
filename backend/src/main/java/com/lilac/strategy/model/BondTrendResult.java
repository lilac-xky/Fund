package com.lilac.strategy.model;

import com.lilac.enums.BondTrendSignal;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 债券趋势判定结果，横盘天数、斜率与波动率一并给出，供提醒与页面展示
 */
@Data
public class BondTrendResult implements Serializable {

    /**
     * 信号，横盘、趋势转弱、正常或数据不足
     */
    private BondTrendSignal signal;

    /**
     * 连续横盘的交易日数
     */
    private Integer sidewaysDays;

    /**
     * 横盘区间起点日期
     */
    private LocalDate sidewaysStart;

    /**
     * 横盘区间终点日期，即最新净值日
     */
    private LocalDate sidewaysEnd;

    /**
     * 横盘区间累计涨幅，百分点
     */
    private BigDecimal sidewaysReturn;

    /**
     * 回看窗口的交易日数
     */
    private Integer window;

    /**
     * 回看窗口起点日期
     */
    private LocalDate windowStart;

    /**
     * 回看窗口终点日期
     */
    private LocalDate windowEnd;

    /**
     * 回看窗口累计涨幅，百分点
     */
    private BigDecimal windowReturn;

    /**
     * 窗口内日收益对时间回归的斜率，百分点每日
     */
    private BigDecimal slope;

    /**
     * 斜率年化后的数值，百分点每年
     */
    private BigDecimal annualizedSlope;

    /**
     * 窗口内日收益标准差，百分点
     */
    private BigDecimal volatility;

    /**
     * 参与计算的净值点数
     */
    private Integer samples;

    /**
     * 判定依据文字，逐条可读，必须含具体数值
     */
    private List<String> remarks = new ArrayList<>();

    /**
     * 数据不足或无法判断时的原因
     */
    private String unavailableReason;

    private static final long serialVersionUID = 1L;
}
