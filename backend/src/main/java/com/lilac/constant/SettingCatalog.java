package com.lilac.constant;

/**
 * 参数设置页的分组元数据，集中维护字段定义、默认值与校验范围
 */
public final class SettingCatalog {

    /**
     * 页面顺序，与 ParamGroup 枚举同名
     */
    public static final String GROUP_DIVIDEND = "DIVIDEND";

    /**
     * 网格分组名
     */
    public static final String GROUP_GRID = "GRID";

    /**
     * 指标分组名
     */
    public static final String GROUP_INDICATOR = "INDICATOR";

    /**
     * 警示分组名
     */
    public static final String GROUP_ALERT = "ALERT";

    /**
     * 控件类型：文本
     */
    public static final String TYPE_TEXT = "TEXT";

    /**
     * 控件类型：小数
     */
    public static final String TYPE_DECIMAL = "DECIMAL";

    /**
     * 控件类型：整数
     */
    public static final String TYPE_INTEGER = "INTEGER";

    /**
     * 控件类型：开关
     */
    public static final String TYPE_BOOLEAN = "BOOLEAN";

    /**
     * 警示参数：单只基金连续超阈值多少期后提示停用
     */
    public static final String ALERT_SUSPEND_STREAK = "suspendStreak";

    /**
     * 警示参数：股息率数据超过多少天标为过期
     */
    public static final String ALERT_DIVIDEND_STALE_DAYS = "dividendStaleDays";

    /**
     * 连续超阈值期数的默认值，与 EstimateThresholds 的默认一致
     */
    public static final String DEFAULT_SUSPEND_STREAK = "3";

    /**
     * 股息率过期天数的默认值，与 application.yml 的 stale-days 一致
     */
    public static final String DEFAULT_DIVIDEND_STALE_DAYS = "7";

    /**
     * 债券误差阈值的默认值，与 application.yml 的 bond-error-threshold 一致
     */
    public static final String DEFAULT_BOND_ERROR_THRESHOLD = "0.10";

    private SettingCatalog() {
    }
}
