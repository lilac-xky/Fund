package com.lilac.domain.dto.holding;

import com.lilac.enums.HoldingStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓更新请求
 */
@Data
public class HoldingUpdateRequest implements Serializable {

    /**
     * 主键
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
     * 建仓日期
     */
    private LocalDate openDate;

    /**
     * 持仓状态
     */
    private HoldingStatus status;

    private static final long serialVersionUID = 1L;
}
