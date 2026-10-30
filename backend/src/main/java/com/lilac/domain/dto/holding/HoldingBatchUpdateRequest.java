package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓批次更正请求，录入时填错份额、成本价或建仓日期用它改正
 */
@Data
public class HoldingBatchUpdateRequest implements Serializable {

    /**
     * 持仓批次主键
     */
    private Long id;

    /**
     * 更正后的份额，必须为正数
     */
    private BigDecimal shares;

    /**
     * 更正后的成本价，必须为正数
     */
    private BigDecimal costPrice;

    /**
     * 更正后的建仓日期，为空则不改
     */
    private LocalDate openDate;

    private static final long serialVersionUID = 1L;
}
