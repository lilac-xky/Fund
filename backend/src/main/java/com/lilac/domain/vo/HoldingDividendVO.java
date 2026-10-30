package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一次除息明细，用于页面核对分红金额
 */
@Data
public class HoldingDividendVO implements Serializable {

    /**
     * 除息日，即单位净值被扣掉分红的那一天
     */
    private LocalDate date;

    /**
     * 每份现金分红金额，单位元
     */
    private BigDecimal perShare;

    /**
     * 该次除息实际计入的持有份额，除息日之后才买入的批次不计入
     */
    private BigDecimal shares;

    /**
     * 该次除息对应到当时持有份额的金额，单位元
     */
    private BigDecimal amount;

    private static final long serialVersionUID = 1L;
}
