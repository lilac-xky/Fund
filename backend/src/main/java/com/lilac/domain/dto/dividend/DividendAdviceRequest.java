package com.lilac.domain.dto.dividend;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单只红利品种的建议请求，金额留空时按持仓份额与最新净值推算
 */
@Data
public class DividendAdviceRequest implements Serializable {

    /**
     * 品种业务主键，如 OF:090010
     */
    private String varietyKey;

    /**
     * 当前持有金额，单位元；留空则用持仓份额 × 最新净值
     */
    private BigDecimal currentAmount;

    private static final long serialVersionUID = 1L;
}
