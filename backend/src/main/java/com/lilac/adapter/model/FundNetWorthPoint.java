package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金净值曲线上的一个交易日
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundNetWorthPoint {

    /**
     * 净值日期
     */
    private LocalDate netWorthDate;

    /**
     * 单位净值
     */
    private BigDecimal unitNetWorth;

    /**
     * 当日净值回报，单位百分点
     */
    private BigDecimal equityReturn;

    /**
     * 累计净值，含历史现金分红；上游未提供时为 null
     */
    private BigDecimal accumulatedNetWorth;

    /**
     * 构造不含累计净值的净值点
     *
     * @param netWorthDate 净值日期
     * @param unitNetWorth 单位净值
     * @param equityReturn 当日净值回报
     */
    public FundNetWorthPoint(LocalDate netWorthDate, BigDecimal unitNetWorth, BigDecimal equityReturn) {
        this.netWorthDate = netWorthDate;
        this.unitNetWorth = unitNetWorth;
        this.equityReturn = equityReturn;
    }
}
