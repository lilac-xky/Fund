package com.lilac.constant;

import java.math.BigDecimal;

/**
 * 红利策略参数在 strategy_param 表中的键名与默认值，分组固定为 DIVIDEND
 */
public final class DividendParamKey {

    /**
     * 基准仓位金额，单位元，股息率高于加仓阈值时补到该金额
     */
    public static final String BASE_AMOUNT = "baseAmount";

    /**
     * 保底分红金额，单位元，任何减仓后的剩余金额都不得低于该值
     */
    public static final String FLOOR_AMOUNT = "floorAmount";

    /**
     * 加仓股息率阈值，单位百分点，股息率不低于该值时加仓
     */
    public static final String ADD_YIELD = "addYield";

    /**
     * 减仓股息率阈值，单位百分点，股息率不高于该值时减仓
     */
    public static final String REDUCE_YIELD = "reduceYield";

    /**
     * 基准仓位金额的参数说明，写入 setting 页时直接展示
     */
    public static final String BASE_AMOUNT_DESC = "红利基准仓位金额，单位元；股息率不低于加仓阈值时补到该金额";

    /**
     * 保底分红金额的参数说明
     */
    public static final String FLOOR_AMOUNT_DESC = "红利保底分红金额，单位元；减仓后的剩余金额不得低于该值";

    /**
     * 加仓股息率阈值的参数说明
     */
    public static final String ADD_YIELD_DESC = "加仓股息率阈值，单位百分点，如 5.00 表示 5.00%";

    /**
     * 减仓股息率阈值的参数说明
     */
    public static final String REDUCE_YIELD_DESC = "减仓股息率阈值，单位百分点，如 3.00 表示 3.00%";

    /**
     * 加仓阈值默认值，百分点；中证红利近年股息率多在 3%~6%，5% 是可用起点
     */
    public static final BigDecimal DEFAULT_ADD_YIELD = new BigDecimal("5.00");

    /**
     * 减仓阈值默认值，百分点
     */
    public static final BigDecimal DEFAULT_REDUCE_YIELD = new BigDecimal("3.00");

    private DividendParamKey() {
    }
}
