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

    private static final long serialVersionUID = 1L;
}
