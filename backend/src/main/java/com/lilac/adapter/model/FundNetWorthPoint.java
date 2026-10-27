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
}
