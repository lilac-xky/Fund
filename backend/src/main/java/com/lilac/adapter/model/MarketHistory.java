package com.lilac.adapter.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 一只标的的日线序列，带复权口径与取数来源，供区间收益与回溯取价
 */
@Data
public class MarketHistory {

    /**
     * 行情标的
     */
    private MarketSymbol symbol;

    /**
     * 日线，按日期升序
     */
    private List<MarketBar> bars;

    /**
     * 是否为前复权口径
     */
    private Boolean adjusted;

    /**
     * 取数来源，tencent / sina
     */
    private String source;

    /**
     * 取数时间，供缓存与排障核对
     */
    private LocalDate fetchDate;

    /**
     * 最新一根日线的收盘价，无数据返回 null
     *
     * @return 最新收盘价
     */
    public java.math.BigDecimal latestClose() {
        return bars == null || bars.isEmpty() ? null : bars.get(bars.size() - 1).getClose();
    }

    /**
     * 最新一根日线的日期，无数据返回 null
     *
     * @return 最新交易日
     */
    public LocalDate latestDate() {
        return bars == null || bars.isEmpty() ? null : bars.get(bars.size() - 1).getDate();
    }

    /**
     * 是否有足够根数计算区间收益
     *
     * @param minimum 最低根数
     * @return 足够返回 true
     */
    public boolean enough(int minimum) {
        return bars != null && bars.size() >= minimum;
    }
}
