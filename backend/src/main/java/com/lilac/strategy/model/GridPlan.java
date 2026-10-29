package com.lilac.strategy.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 网格策略参数，价格单位元，每格金额单位元
 */
@Data
public class GridPlan {

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
     * 价格穿越一条网格线时建议买卖的金额，单位元
     */
    private BigDecimal amountPerGrid;

    /**
     * 参数问题清单，为空表示可用于计算网格
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数是否齐全且互相自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && lowerBound != null && upperBound != null
                && step != null && amountPerGrid != null;
    }
}
