package com.lilac.domain.dto.grid;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单只标的的网格试算请求，用于核对划格与触发是否符合预期
 */
@Data
public class GridAdviceRequest implements Serializable {

    /**
     * 品种业务主键，为空时按全部持有中的场内 ETF
     */
    private String varietyKey;

    /**
     * 手工指定的当前价，留空则取行情
     */
    private BigDecimal currentPrice;

    /**
     * 手工指定的参考价，留空则取上一交易日收盘价
     */
    private BigDecimal referencePrice;

    private static final long serialVersionUID = 1L;
}
