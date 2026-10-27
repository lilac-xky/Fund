package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日线中的一个收盘价，供区间收益与滚动估计使用
 */
@Data
public class MarketBar {

    /**
     * 交易日
     */
    private LocalDate date;

    /**
     * 收盘价
     */
    private BigDecimal close;

    /**
     * 构造一根日线
     *
     * @param date  交易日
     * @param close 收盘价
     */
    public MarketBar(LocalDate date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }
}
