package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 债券监控参数视图，含可用性与问题清单
 */
@Data
public class BondPlanVO implements Serializable {

    /**
     * 回看窗口，交易日
     */
    private Integer window;

    /**
     * 判定横盘的单日波动上限，百分点
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
     * 年化斜率转弱阈值，百分点
     */
    private BigDecimal weakSlope;

    /**
     * 判定趋势转弱的波动率下限，百分点
     */
    private BigDecimal weakVolatility;

    /**
     * 参数是否齐全且自洽
     */
    private Boolean usable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> issues;

    private static final long serialVersionUID = 1L;
}
