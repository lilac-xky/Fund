package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓买入请求，品种须先经消歧确认写入注册表
 */
@Data
public class HoldingBuyRequest implements Serializable {

    /**
     * 所持品种的业务主键，如 OF:000001
     */
    private String varietyKey;

    /**
     * 买入份额，必须为正数
     */
    private BigDecimal shares;

    /**
     * 买入成本价，必须为正数
     */
    private BigDecimal costPrice;

    /**
     * 建仓日期，为空时取当日
     */
    private LocalDate openDate;

    private static final long serialVersionUID = 1L;
}
