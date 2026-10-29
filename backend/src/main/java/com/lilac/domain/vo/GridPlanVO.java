package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 网格参数视图，附问题清单供设置页提示不合法组合
 */
@Data
public class GridPlanVO implements Serializable {

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

    /**
     * 网格线序列，参数可用时给出，供页面直接展示划格结果
     */
    private List<BigDecimal> lines;

    /**
     * 参数是否齐全且自洽
     */
    private Boolean usable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> issues;

    private static final long serialVersionUID = 1L;
}
