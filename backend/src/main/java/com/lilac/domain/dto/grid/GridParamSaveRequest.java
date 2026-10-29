package com.lilac.domain.dto.grid;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 网格参数保存请求，按标的分开配置时带品种主键，四项都是必填
 */
@Data
public class GridParamSaveRequest implements Serializable {

    /**
     * 品种业务主键；留空表示保存通用参数，供尚未逐只配置的标的兜底
     */
    private String varietyKey;

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
