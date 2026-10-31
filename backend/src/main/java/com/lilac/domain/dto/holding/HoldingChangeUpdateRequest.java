package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 卖出记录更正请求，只改业务日期与订单净值，不重算份额扣减
 */
@Data
public class HoldingChangeUpdateRequest implements Serializable {

    /**
     * 变动记录主键
     */
    private Long id;

    /**
     * 更正后的卖出日期，为空则不改
     */
    private LocalDate tradeDate;

    /**
     * 更正后的卖出净值，为空则不改
     */
    private BigDecimal unitPrice;

    private static final long serialVersionUID = 1L;
}
