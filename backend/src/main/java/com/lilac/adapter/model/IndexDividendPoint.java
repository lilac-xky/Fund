package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一个指数在某一天的估值指标，股息率是红利策略的唯一输入
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexDividendPoint {

    /**
     * 指数代码，如 000922
     */
    private String indexCode;

    /**
     * 数据日期，即该行估值所属的交易日
     */
    private LocalDate dataDate;

    /**
     * 指数中文全称
     */
    private String indexName;

    /**
     * 股息率1（总股本），单位百分点
     */
    private BigDecimal dividendYield1;

    /**
     * 股息率2（计算用股本），单位百分点
     */
    private BigDecimal dividendYield2;

    /**
     * 取一档可用于策略的股息率，优先计算用股本口径
     *
     * @return 股息率，两档都缺失时返回 null
     */
    public BigDecimal usableYield() {
        return dividendYield2 != null ? dividendYield2 : dividendYield1;
    }
}
