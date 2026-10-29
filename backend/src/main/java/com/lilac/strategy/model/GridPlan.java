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
     * 所属品种业务主键；为 null 表示尚未按标的拆分的历史全局参数
     */
    private String varietyKey;

    /**
     * 所属品种名称，供页面显示「这套格子管的是哪只」，全局旧参数为 null
     */
    private String varietyName;

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
     * 参数问题清单，非空表示不可用于计算网格
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数提示，如「正在沿用通用参数」；不影响参数是否可用
     */
    private List<String> notes = new ArrayList<>();

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
