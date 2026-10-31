package com.lilac.strategy.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 债券监控参数，含回看窗口、横盘判定与趋势转弱阈值
 */
@Data
public class BondPlan implements Serializable {

    /**
     * 判断横盘与斜率的回看窗口，交易日
     */
    private Integer window;

    /**
     * 判定横盘的单日净值波动上限，百分点
     */
    private BigDecimal sidewaysBand;

    /**
     * 触发换仓提醒的横盘交易日数
     */
    private Integer sidewaysDays;

    /**
     * 横盘期累计涨幅下限，百分点
     */
    private BigDecimal sidewaysReturn;

    /**
     * 回看窗口内年化斜率的转弱阈值，百分点
     */
    private BigDecimal weakSlope;

    /**
     * 判定趋势转弱的波动率下限，百分点
     */
    private BigDecimal weakVolatility;

    /**
     * 参数问题清单，空表示可用
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数是否齐全且自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && window != null && sidewaysBand != null && sidewaysDays != null
                && sidewaysReturn != null && weakSlope != null && weakVolatility != null;
    }

    private static final long serialVersionUID = 1L;
}
