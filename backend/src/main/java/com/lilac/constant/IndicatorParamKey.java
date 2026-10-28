package com.lilac.constant;

/**
 * 技术指标参数在 strategy_param 表中的键名与默认值，分组固定为 INDICATOR
 */
public final class IndicatorParamKey {

    /**
     * RSI 计算周期
     */
    public static final String RSI_PERIOD = "rsiPeriod";

    /**
     * RSI 超买阈值
     */
    public static final String RSI_UPPER = "rsiUpper";

    /**
     * RSI 超卖阈值
     */
    public static final String RSI_LOWER = "rsiLower";

    /**
     * KDJ 的 RSV 周期
     */
    public static final String KDJ_PERIOD = "kdjPeriod";

    /**
     * KDJ 的 K 平滑系数
     */
    public static final String KDJ_K_SMOOTH = "kdjKSmooth";

    /**
     * KDJ 的 D 平滑系数
     */
    public static final String KDJ_D_SMOOTH = "kdjDSmooth";

    /**
     * MACD 快线周期
     */
    public static final String MACD_FAST = "macdFast";

    /**
     * MACD 慢线周期
     */
    public static final String MACD_SLOW = "macdSlow";

    /**
     * MACD 信号线周期
     */
    public static final String MACD_SIGNAL = "macdSignal";

    /**
     * 指标信号是否参与收盘前邮件提醒
     */
    public static final String PARTICIPATE_IN_ALERT = "participateInAlert";

    /**
     * RSI 周期的默认值，业内通用 14 日
     */
    public static final String DEFAULT_RSI_PERIOD = "14";

    /**
     * RSI 超买阈值的默认值
     */
    public static final String DEFAULT_RSI_UPPER = "70";

    /**
     * RSI 超卖阈值的默认值
     */
    public static final String DEFAULT_RSI_LOWER = "30";

    /**
     * KDJ 的 RSV 周期默认值
     */
    public static final String DEFAULT_KDJ_PERIOD = "9";

    /**
     * KDJ 的 K 平滑系数默认值
     */
    public static final String DEFAULT_KDJ_K_SMOOTH = "3";

    /**
     * KDJ 的 D 平滑系数默认值
     */
    public static final String DEFAULT_KDJ_D_SMOOTH = "3";

    /**
     * MACD 快线周期默认值
     */
    public static final String DEFAULT_MACD_FAST = "12";

    /**
     * MACD 慢线周期默认值
     */
    public static final String DEFAULT_MACD_SLOW = "26";

    /**
     * MACD 信号线周期默认值
     */
    public static final String DEFAULT_MACD_SIGNAL = "9";

    /**
     * 指标是否参与提醒的默认值，默认参与
     */
    public static final String DEFAULT_PARTICIPATE_IN_ALERT = "true";

    private IndicatorParamKey() {
    }
}
