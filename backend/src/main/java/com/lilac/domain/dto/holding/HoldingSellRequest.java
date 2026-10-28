package com.lilac.domain.dto.holding;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 持仓卖出请求，按先进先出扣减已有持仓
 */
@Data
public class HoldingSellRequest implements Serializable {

    /**
     * 所持品种的业务主键，如 OF:000001
     */
    private String varietyKey;

    /**
     * 卖出份额，必须为正数且不超过当前持有合计
     */
    private BigDecimal shares;

    private static final long serialVersionUID = 1L;
}
