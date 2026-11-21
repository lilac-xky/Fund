package com.lilac.constant;

import java.math.BigDecimal;

/**
 * 红利策略参数在 strategy_param 表中的键名，分组固定为 DIVIDEND，参数按标的各存一套
 */
public final class DividendParamKey {

    /**
     * 股息率阶梯，值是一段 JSON 数组，每档含 yield 与 amount
     */
    public static final String LADDER = "ladder";

    /**
     * 按标的存放时的键分隔符，形如 OF:007467.ladder
     */
    public static final String PER_VARIETY_SEPARATOR = ".";

    /**
     * 股息率阶梯的参数说明
     */
    public static final String LADDER_DESC = "股息率阶梯，每档写「这个股息率下该持有多少钱」，最低一档即保底底仓";

    /**
     * 加仓阈值默认值，百分点；中证红利近年股息率多在 3%~6%，5% 是可用起点
     */
    public static final BigDecimal DEFAULT_ADD_YIELD = new BigDecimal("5.00");

    /**
     * 减仓阈值默认值，百分点
     */
    public static final BigDecimal DEFAULT_REDUCE_YIELD = new BigDecimal("3.00");

    /**
     * 单只基金最少要有几档，只有一档时无法表达越跌越买
     */
    public static final int MIN_RUNGS = 2;

    /**
     * 单只基金最多允许几档，防止页面误填出上百档
     */
    public static final int MAX_RUNGS = 12;

    private DividendParamKey() {
    }

    /**
     * 拼出某只标的阶梯的参数键
     *
     * @param varietyKey 品种业务主键，如 OF:007467
     * @return 参数键，如 OF:007467.ladder
     */
    public static String keyOf(String varietyKey) {
        return varietyKey + PER_VARIETY_SEPARATOR + LADDER;
    }

    /**
     * 从参数键里取出品种业务主键，旧版的单键参数返回 null
     *
     * @param paramKey 参数键
     * @return 品种业务主键，非阶梯键返回 null
     */
    public static String varietyKeyOf(String paramKey) {
        if (paramKey == null) {
            return null;
        }
        int index = paramKey.lastIndexOf(PER_VARIETY_SEPARATOR);
        if (index <= 0) {
            return null;
        }
        // 只有后半段就是阶梯字段名才认作按标的存放的键，避免把旧键误判
        return LADDER.equals(paramKey.substring(index + 1)) ? paramKey.substring(0, index) : null;
    }
}
