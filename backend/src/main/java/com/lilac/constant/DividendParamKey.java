package com.lilac.constant;

import java.math.BigDecimal;

/**
 * 红利策略参数在 strategy_param 表中的键名与默认值，分组固定为 DIVIDEND，参数按标的各存一套
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
     * 按标的存放时的键分隔符，形如 OF:007467.baseAmount
     */
    public static final String PER_VARIETY_SEPARATOR = ".";

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

    /**
     * 四个字段名的固定顺序，读写都按它遍历
     */
    public static final String[] FIELDS = {BASE_AMOUNT, FLOOR_AMOUNT, ADD_YIELD, REDUCE_YIELD};

    private DividendParamKey() {
    }

    /**
     * 拼出某只标的某个字段的参数键
     *
     * @param varietyKey 品种业务主键，如 OF:007467
     * @param field      字段名，如 baseAmount
     * @return 参数键，如 OF:007467.baseAmount
     */
    public static String keyOf(String varietyKey, String field) {
        return varietyKey + PER_VARIETY_SEPARATOR + field;
    }

    /**
     * 从参数键里取出品种业务主键，未按标的存放的旧键返回 null
     *
     * @param paramKey 参数键
     * @return 品种业务主键，非按标的存放的键返回 null
     */
    public static String varietyKeyOf(String paramKey) {
        if (paramKey == null) {
            return null;
        }
        int index = paramKey.lastIndexOf(PER_VARIETY_SEPARATOR);
        if (index <= 0) {
            return null;
        }
        String field = paramKey.substring(index + 1);
        // 只有后半段是已知字段名才认作按标的存放的键，避免把别的键误判
        for (String known : FIELDS) {
            if (known.equals(field)) {
                return paramKey.substring(0, index);
            }
        }
        return null;
    }
}
