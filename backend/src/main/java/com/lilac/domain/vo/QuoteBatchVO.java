package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 批量行情结果，带出请求数与可用数，便于调用方判断覆盖率是否下降
 */
@Data
public class QuoteBatchVO implements Serializable {

    /**
     * 逐只行情，按请求顺序
     */
    private List<MarketQuoteVO> quotes;

    /**
     * 请求的标的数
     */
    private Integer requested;

    /**
     * 可用于预估的标的数
     */
    private Integer usableCount;

    /**
     * 停牌的标的数
     */
    private Integer suspendedCount;

    /**
     * 无行情的标的数，含 920xxx 北交所
     */
    private Integer noDataCount;

    /**
     * 本次是否有标的走了备用源
     */
    private Boolean fallbackUsed;

    /**
     * 数据日期，取可用标的中最新的交易日
     */
    private LocalDate dataDate;

    /**
     * 是否处于交易时段，false 时数据为上一交易日
     */
    private Boolean tradingHours;

    private static final long serialVersionUID = 1L;
}
