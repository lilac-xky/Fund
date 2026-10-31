package com.lilac.constant;

/**
 * 债券监控参数在 strategy_param 表中的键名与默认值，分组固定为 BOND
 */
public final class BondParamKey {

    /**
     * 判断横盘与斜率的回看窗口，交易日
     */
    public static final String WINDOW = "window";

    /**
     * 判定横盘的单日净值波动上限，百分点
     */
    public static final String SIDEWAYS_BAND = "sidewaysBand";

    /**
     * 触发换仓提醒的横盘交易日数
     */
    public static final String SIDEWAYS_DAYS = "sidewaysDays";

    /**
     * 触发换仓提醒的累计涨幅下限，百分点，为正表示要求至少涨这么多
     */
    public static final String SIDEWAYS_RETURN = "sidewaysReturn";

    /**
     * 回看窗口内年化斜率的转弱阈值，百分点
     */
    public static final String WEAK_SLOPE = "weakSlope";

    /**
     * 判定为趋势转弱的波动率下限，百分点
     */
    public static final String WEAK_VOLATILITY = "weakVolatility";

    /**
     * 回看窗口的默认值，约一个季度交易日
     */
    public static final String DEFAULT_WINDOW = "60";

    /**
     * 单日波动上限的默认值，债券日波动常在 0.02 上下
     */
    public static final String DEFAULT_SIDEWAYS_BAND = "0.02";

    /**
     * 横盘交易日数的默认值，接近使用者原话「一个月没涨」
     */
    public static final String DEFAULT_SIDEWAYS_DAYS = "20";

    /**
     * 横盘期累计涨幅下限的默认值，百分点
     */
    public static final String DEFAULT_SIDEWAYS_RETURN = "0.10";

    /**
     * 年化斜率转弱阈值的默认值，百分点
     */
    public static final String DEFAULT_WEAK_SLOPE = "0.50";

    /**
     * 趋势转弱波动率下限的默认值，百分点
     */
    public static final String DEFAULT_WEAK_VOLATILITY = "0.30";

    /**
     * 回看窗口的参数说明
     */
    public static final String WINDOW_DESC = "判断横盘与斜率的回看交易日数";

    /**
     * 单日波动上限的参数说明
     */
    public static final String SIDEWAYS_BAND_DESC = "单日净值涨跌不超过该值即算横盘一天，单位百分点";

    /**
     * 横盘交易日数的参数说明
     */
    public static final String SIDEWAYS_DAYS_DESC = "连续横盘达到多少个交易日即提醒考虑换仓";

    /**
     * 横盘期累计涨幅下限的参数说明
     */
    public static final String SIDEWAYS_RETURN_DESC = "横盘区间累计涨幅低于该值才算横盘，单位百分点";

    /**
     * 年化斜率转弱阈值的参数说明
     */
    public static final String WEAK_SLOPE_DESC = "回看窗口年化斜率低于该值即判为趋势转弱，单位百分点";

    /**
     * 趋势转弱波动率下限的参数说明
     */
    public static final String WEAK_VOLATILITY_DESC = "日波动率低于该值时斜率再负也只算横盘，单位百分点";

    private BondParamKey() {
    }
}
