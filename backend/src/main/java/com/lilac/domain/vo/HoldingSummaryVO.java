package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 候选项的持仓回显，品种已在注册表时带出当前持有状态
 */
@Data
public class HoldingSummaryVO implements Serializable {

    /**
     * 是否已有持有中持仓
     */
    private Boolean holding;

    /**
     * 合计持有份额，无持仓为 null
     */
    private BigDecimal totalShares;

    /**
     * 加权平均成本价，无持仓为 null
     */
    private BigDecimal avgCostPrice;

    private static final long serialVersionUID = 1L;
}
