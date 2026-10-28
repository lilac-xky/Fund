package com.lilac.constant;

/**
 * 警示参数在 strategy_param 表中的键名与默认值，分组固定为 ALERT
 */
public final class AlertParamKey {

    /**
     * 单只基金连续多少期误差超阈值后提示停用预估
     */
    public static final String SUSPEND_STREAK = "suspendStreak";

    /**
     * 股息率数据超过多少天标为过期
     */
    public static final String DIVIDEND_STALE_DAYS = "dividendStaleDays";

    /**
     * 债券型的误差阈值，百分点；改这里会真实覆盖配置文件里的同名阈值
     */
    public static final String BOND_ERROR_THRESHOLD = "bondErrorThreshold";

    private AlertParamKey() {
    }
}
