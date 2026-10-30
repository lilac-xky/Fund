package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一条持有中的持仓批次，录入出错时按它更正或撤销
 */
@Data
public class HoldingBatchVO implements Serializable {

    /**
     * 持仓批次主键
     */
    private Long id;

    /**
     * 持有份额
     */
    private BigDecimal shares;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 成本金额，份额乘成本价
     */
    private BigDecimal costAmount;

    /**
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 录入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
