package com.lilac.constant;

/**
 * 历史教训警示的参数键与默认值，参数落 strategy_param 表
 */
public final class LessonParamKey {

    /**
     * 追高判定：买入价高于近期高点的容忍幅度，百分点
     */
    public static final String CHASE_TOLERANCE = "chaseTolerance";

    /**
     * 追高判定：近期高点的回看交易日数
     */
    public static final String CHASE_WINDOW = "chaseWindow";

    /**
     * 恐慌卖出判定：连续亏损达多少个交易日才算
     */
    public static final String PANIC_LOSS_DAYS = "panicLossDays";

    /**
     * 恐慌卖出判定：回看窗口内累计亏损率下限，百分点
     */
    public static final String PANIC_LOSS_RATE = "panicLossRate";

    /**
     * 盲目换仓判定：换入品种须在多少天内登记过才算「熟悉」
     */
    public static final String FAMILIAR_DAYS = "familiarDays";

    /**
     * 警示是否随收盘前邮件发出
     */
    public static final String PARTICIPATE_IN_ALERT = "lessonParticipateInAlert";

    /**
     * 追高容忍幅度的默认值，百分比
     */
    public static final String DEFAULT_CHASE_TOLERANCE = "3";

    /**
     * 追高回看窗口的默认值，交易日
     */
    public static final String DEFAULT_CHASE_WINDOW = "20";

    /**
     * 连续亏损天数的默认值
     */
    public static final String DEFAULT_PANIC_LOSS_DAYS = "5";

    /**
     * 累计亏损率下限的默认值，百分点
     */
    public static final String DEFAULT_PANIC_LOSS_RATE = "5";

    /**
     * 熟悉品种天数的默认值
     */
    public static final String DEFAULT_FAMILIAR_DAYS = "30";

    /**
     * 是否参与提醒的默认值
     */
    public static final String DEFAULT_PARTICIPATE_IN_ALERT = "true";

    /**
     * 追高容忍幅度的参数说明
     */
    public static final String CHASE_TOLERANCE_DESC = "买入价高于近期高点的容忍幅度，超过即提示追高";

    /**
     * 追高回看窗口的参数说明
     */
    public static final String CHASE_WINDOW_DESC = "判断近期高点所用的交易日数";

    /**
     * 连续亏损天数的参数说明
     */
    public static final String PANIC_LOSS_DAYS_DESC = "持仓连续亏损多少个交易日才提示恐慌卖出";

    /**
     * 累计亏损率下限的参数说明
     */
    public static final String PANIC_LOSS_RATE_DESC = "回看窗口内累计亏损达到该值才提示恐慌卖出，单位百分点";

    /**
     * 熟悉品种天数的参数说明
     */
    public static final String FAMILIAR_DAYS_DESC = "换入品种登记不足该天数即视为不熟悉，单位天";

    /**
     * 是否参与提醒的参数说明
     */
    public static final String PARTICIPATE_IN_ALERT_DESC = "关闭后只在页面展示，不随收盘前邮件发出";

    private LessonParamKey() {
    }
}
