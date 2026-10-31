package com.lilac.domain.vo;

import com.lilac.enums.BondTrendSignal;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只债券基金的趋势监控结果，横盘天数与趋势指标必须给出具体数值
 */
@Data
public class BondMonitorVO implements Serializable {

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
     * 信号，横盘、趋势转弱、正常或数据不足
     */
    private BondTrendSignal signal;

    /**
     * 信号中文名
     */
    private String signalLabel;

    /**
     * 是否产生换仓提醒
     */
    private Boolean alerting;

    /**
     * 连续横盘的交易日数
     */
    private Integer sidewaysDays;

    /**
     * 横盘区间起点日期
     */
    private LocalDate sidewaysStart;

    /**
     * 横盘区间终点日期
     */
    private LocalDate sidewaysEnd;

    /**
     * 横盘区间的自然日跨度
     */
    private Long sidewaysCalendarDays;

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
     * 日收益回归斜率，百分点每日
     */
    private BigDecimal slope;

    /**
     * 年化斜率，百分点每年
     */
    private BigDecimal annualizedSlope;

    /**
     * 日收益标准差，百分点
     */
    private BigDecimal volatility;

    /**
     * 参与计算的净值点数
     */
    private Integer samples;

    /**
     * 最新净值日期
     */
    private LocalDate latestNetWorthDate;

    /**
     * 当前持有金额，单位元
     */
    private BigDecimal currentAmount;

    /**
     * 当前金额来源说明
     */
    private String amountSource;

    /**
     * 判定依据文字，逐条带数值
     */
    private List<String> remarks;

    /**
     * 数据不足时的原因
     */
    private String unavailableReason;

    private static final long serialVersionUID = 1L;
}
