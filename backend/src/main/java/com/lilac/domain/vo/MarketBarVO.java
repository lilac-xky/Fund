package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日线中的一根，供指标计算与回溯取价
 */
@Data
public class MarketBarVO implements Serializable {

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
     * 最高价，KDJ 的 RSV 必须用它
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

    private static final long serialVersionUID = 1L;
}
