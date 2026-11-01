package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 变动记录更正请求，卖出改日期与净值，买入改份额与净值
 */
@Data
public class HoldingChangeUpdateRequest implements Serializable {

    /**
     * 变动记录主键
     */
    private Long id;

    /**
     * 更正后的业务日期，为空则不改
     */
    private LocalDate tradeDate;

    /**
     * 更正后的成交净值，为空则不改
     */
    private BigDecimal unitPrice;

    /**
     * 更正后的份额，只对买入生效，为空则不改
     */
    private BigDecimal shares;

    private static final long serialVersionUID = 1L;
}
