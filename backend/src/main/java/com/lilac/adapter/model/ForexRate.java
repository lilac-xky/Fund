package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 港币兑人民币汇率，日线与当日变动分开给出，供港股收益折算
 */
@Data
public class ForexRate {

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
     * 当日变动，小数形式，如 0.0021 表示升值 0.21%
     */
    private BigDecimal changeRate;

    /**
     * 汇率数据日期
     */
    private LocalDate tradeDate;

    /**
     * 取数来源，tencent / sina
     */
    private String source;

    /**
     * 是否可用于折算，汇率与变动都取到才为 true
     *
     * @return 可用返回 true
     */
    public boolean usable() {
        return rate != null && changeRate != null && tradeDate != null;
    }
}
