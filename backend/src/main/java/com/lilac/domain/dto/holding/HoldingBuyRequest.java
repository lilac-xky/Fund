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
     * 买入份额，必须为正数；与 amount 二选一，都传时以份额为准
     */
    private BigDecimal shares;

    /**
     * 买入成本价，必须为正数；缺省时按当日或之前最近净值自动带出
     */
    private BigDecimal costPrice;

    /**
     * 买入金额，元；支付宝订单只有金额时用它，份额按当日净值折算
     */
    private BigDecimal amount;

    /**
     * 建仓日期，为空时取当日
     */
    private LocalDate openDate;

    /**
     * 来源交易号，账单导入时带上，用于跨次导入去重
     */
    private String tradeNo;

    private static final long serialVersionUID = 1L;
}
