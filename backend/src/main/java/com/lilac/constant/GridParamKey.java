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
     * 按标的存放时的键分隔符，形如 OF:510300.lowerBound
     */
    public static final String PER_VARIETY_SEPARATOR = ".";

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

    /**
     * 四个字段名的固定顺序，读写都按它遍历
     */
    public static final String[] FIELDS = {LOWER_BOUND, UPPER_BOUND, STEP, AMOUNT_PER_GRID};

    private GridParamKey() {
    }

    /**
     * 拼出某只标的某个字段的参数键
     *
     * @param varietyKey 品种业务主键，如 OF:510300
     * @param field      字段名，如 lowerBound
     * @return 参数键，如 OF:510300.lowerBound
     */
    public static String keyOf(String varietyKey, String field) {
        return varietyKey + PER_VARIETY_SEPARATOR + field;
    }

    /**
     * 从参数键里取出品种业务主键，全局旧键返回 null
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
