package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一只标的的当日行情，带可用性状态、数据日期与取数来源
 */
@Data
public class MarketQuote {

    /**
     * 行情标的
     */
    private MarketSymbol symbol;

    /**
     * 标的名称
     */
    private String name;

    /**
     * 最新价
     */
    private BigDecimal price;

    /**
     * 昨收，除权除息日由数据源给的含权昨收，保证当日涨跌幅不出现假跳空
     */
    private BigDecimal previousClose;

    /**
     * 当日涨跌幅，单位百分点
     */
    private BigDecimal changePercent;

    /**
     * 数据日期，非交易时段为上一交易日
     */
    private LocalDate tradeDate;

    /**
     * 行情时间戳
     */
    private LocalDateTime quoteTime;

    /**
     * 是否交易时段内产生的数据，false 表示这是上一交易日的收盘数据
     */
    private Boolean realtime;

    /**
     * 可用性状态
     */
    private QuoteStatus status;

    /**
     * 不可用原因，可用时为 null
     */
    private String unavailableReason;

    /**
     * 取数来源，tencent / sina
     */
    private String source;

    /**
     * 涨跌幅是否取自前复权口径，新浪日线为 false 时必须标注
     */
    private Boolean adjusted;

    /**
     * 是否可用于预估，只有 OK 且涨跌幅与日期齐全才为 true
     *
     * @return 可用返回 true
     */
    public boolean usable() {
        return status == QuoteStatus.OK && changePercent != null && tradeDate != null;
    }

    /**
     * 构造一个不可用结果
     *
     * @param symbol 行情标的
     * @param status 状态
     * @param reason 原因
     * @return 不可用行情
     */
    public static MarketQuote unavailable(MarketSymbol symbol, QuoteStatus status, String reason) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(symbol);
        quote.setStatus(status);
        quote.setUnavailableReason(reason);
        quote.setRealtime(Boolean.FALSE);
        quote.setAdjusted(Boolean.TRUE);
        return quote;
    }
}
