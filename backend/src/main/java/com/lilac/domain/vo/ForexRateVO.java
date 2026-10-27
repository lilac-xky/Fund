package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 汇率视图，供页面显示港股持仓的折算依据
 */
@Data
public class ForexRateVO implements Serializable {

    /**
     * 汇率对，如 HKDCNY
     */
    private String pair;

    /**
     * 最新汇率
     */
    private BigDecimal rate;

    /**
     * 上一有效交易日的汇率
     */
    private BigDecimal previousRate;

    /**
     * 当日变动，小数形式
     */
    private BigDecimal changeRate;

    /**
     * 汇率数据日期
     */
    private LocalDate tradeDate;

    /**
     * 取数来源
     */
    private String source;

    /**
     * 是否可用
     */
    private Boolean usable;

    private static final long serialVersionUID = 1L;
}
