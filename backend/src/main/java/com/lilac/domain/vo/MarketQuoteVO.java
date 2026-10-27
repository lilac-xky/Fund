package com.lilac.domain.vo;

import com.lilac.adapter.model.QuoteStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单只标的的行情视图，带可用性状态、数据日期与来源
 */
@Data
public class MarketQuoteVO implements Serializable {

    /**
     * 带交易所前缀的完整代码
     */
    private String symbol;

    /**
     * 标的名称
     */
    private String name;

    /**
     * 最新价
     */
    private BigDecimal price;

    /**
     * 昨收
     */
    private BigDecimal previousClose;

    /**
     * 当日涨跌幅，单位百分点
     */
    private BigDecimal changePercent;

    /**
     * 数据日期
     */
    private LocalDate tradeDate;

    /**
     * 行情时间
     */
    private LocalDateTime quoteTime;

    /**
     * 是否交易时段内的实时数据，false 表示上一交易日
     */
    private Boolean realtime;

    /**
     * 可用性状态
     */
    private QuoteStatus status;

    /**
     * 是否可用于预估
     */
    private Boolean usable;

    /**
     * 不可用原因
     */
    private String unavailableReason;

    /**
     * 取数来源
     */
    private String source;

    /**
     * 是否前复权口径
     */
    private Boolean adjusted;

    private static final long serialVersionUID = 1L;
}
