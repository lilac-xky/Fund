package com.lilac.domain.dto.bond;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 债券监控参数保存请求
 */
@Data
public class BondParamSaveRequest implements Serializable {

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

    private static final long serialVersionUID = 1L;
}
