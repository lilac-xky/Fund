package com.lilac.constant;

/**
 * 网格策略参数在 strategy_param 表中的键名与默认值，分组固定为 GRID
 */
public final class GridParamKey {

    /**
     * 价格下界，单位元
     */
    public static final String LOWER_BOUND = "lowerBound";

    /**
     * 价格上界，单位元
     */
    public static final String UPPER_BOUND = "upperBound";

    /**
     * 格子大小，单位元，相邻网格线的价格间距
     */
    public static final String STEP = "step";

    /**
     * 每格金额，单位元，价格穿越网格线时建议买卖的金额
     */
    public static final String AMOUNT_PER_GRID = "amountPerGrid";

    /**
     * 价格下界的参数说明
     */
    public static final String LOWER_BOUND_DESC = "网格价格下界，单位元";

    /**
     * 价格上界的参数说明
     */
    public static final String UPPER_BOUND_DESC = "网格价格上界，单位元";

    /**
     * 格子大小的参数说明
     */
    public static final String STEP_DESC = "相邻网格线之间的价格间距，单位元";

    /**
     * 每格金额的参数说明
     */
    public static final String AMOUNT_PER_GRID_DESC = "价格穿越网格线时建议买入或卖出的金额，单位元";

    private GridParamKey() {
    }
}
