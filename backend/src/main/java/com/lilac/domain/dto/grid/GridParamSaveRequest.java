package com.lilac.domain.dto.grid;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 网格参数保存请求，四项都是必填
 */
@Data
public class GridParamSaveRequest implements Serializable {

    /**
     * 价格下界，单位元
     */
    private BigDecimal lowerBound;

    /**
     * 价格上界，单位元
     */
    private BigDecimal upperBound;

    /**
     * 相邻网格线的价格间距，单位元
     */
    private BigDecimal step;

    /**
     * 每格金额，单位元
     */
    private BigDecimal amountPerGrid;

    private static final long serialVersionUID = 1L;
}
