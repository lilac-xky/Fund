package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日线中的一根，含开高低收与成交量，供区间收益、滚动估计与指标计算使用
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
     * 开盘价，上游未提供时为 null
     */
    private BigDecimal open;

    /**
     * 最高价，KDJ 的 RSV 必须用它，缺价时该根不参与指标计算
     */
    private BigDecimal high;

    /**
     * 最低价，KDJ 的 RSV 必须用它
     */
    private BigDecimal low;

    /**
     * 成交量，上游未提供时为 null
     */
    private BigDecimal volume;

    /**
     * 构造只含收盘价的日线，供只关心价格的调用方使用
     *
     * @param date  交易日
     * @param close 收盘价
     */
    public MarketBar(LocalDate date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }

    /**
     * 构造完整日线，开高低收与成交量一并带出
     *
     * @param date   交易日
     * @param open   开盘价
     * @param close  收盘价
     * @param high   最高价
     * @param low    最低价
     * @param volume 成交量
     */
    public MarketBar(LocalDate date, BigDecimal open, BigDecimal close, BigDecimal high, BigDecimal low,
                     BigDecimal volume) {
        this.date = date;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }

    /**
     * 是否含可用的最高最低价，KDJ 判断用
     *
     * @return 齐全返回 true
     */
    public boolean hasRange() {
        return high != null && low != null && close != null;
    }
}
