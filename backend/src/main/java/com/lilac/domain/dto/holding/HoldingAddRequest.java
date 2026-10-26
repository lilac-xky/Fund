package com.lilac.domain.dto.holding;

import com.lilac.enums.HoldingStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓新增请求
 */
@Data
public class HoldingAddRequest implements Serializable {

    /**
     * 所持品种的业务主键
     */
    private String varietyKey;

    /**
     * 持有份额
     */
    private BigDecimal shares;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 持仓状态
     */
    private HoldingStatus status;

    private static final long serialVersionUID = 1L;
}
