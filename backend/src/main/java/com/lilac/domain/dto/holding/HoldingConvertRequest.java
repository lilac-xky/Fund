package com.lilac.domain.dto.holding;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金转换请求，把一只品种的份额换成另一只，等于一次卖出加一次买入
 */
@Data
public class HoldingConvertRequest {

    /**
     * 转出的品种业务主键
     */
    private String fromVarietyKey;

    /**
     * 转入的品种业务主键，必须已注册且类型已确认
     */
    private String toVarietyKey;

    /**
     * 转出的份额，必须为正数且不超过当前持有
     */
    private BigDecimal shares;

    /**
     * 转入份额，按转入基金的实际申购份额填写
     */
    private BigDecimal toShares;

    /**
     * 转入成本价，即转入基金的成交净值
     */
    private BigDecimal toCostPrice;

    /**
     * 业务日期，为空时取当日
     */
    private LocalDate openDate;
}
