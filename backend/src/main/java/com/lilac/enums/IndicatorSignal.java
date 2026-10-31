package com.lilac.enums;

/**
 * 技术指标信号，参与策略提醒的触发条件
 */
public enum IndicatorSignal {

    /**
     * RSI 高于超买阈值
     */
    RSI_OVERBOUGHT("RSI 超买", "指标提示短期过热，不宜再追高"),

    /**
     * RSI 低于超卖阈值
     */
    RSI_OVERSOLD("RSI 超卖", "指标提示短期超跌，可留意低吸机会"),

    /**
     * MACD 的 DIF 上穿 DEA
     */
    MACD_GOLDEN_CROSS("MACD 金叉", "快线上穿信号线，短期动能转强"),

    /**
     * MACD 的 DIF 下穿 DEA
     */
    MACD_DEAD_CROSS("MACD 死叉", "快线下穿信号线，短期动能转弱"),

    /**
     * KDJ 的 K 上穿 D 且处于低位
     */
    KDJ_GOLDEN_CROSS("KDJ 金叉", "K 上穿 D 且未进入超买区，短线转强"),

    /**
     * KDJ 的 K 下穿 D 且处于高位
     */
    KDJ_DEAD_CROSS("KDJ 死叉", "K 下穿 D 且已在高位，短线转弱");

    /**
     * 信号名称
     */
    private final String label;

    /**
     * 信号含义，供提醒直接引用
     */
    private final String note;

    IndicatorSignal(String label, String note) {
        this.label = label;
        this.note = note;
    }

    /**
     * 取信号名称
     *
     * @return 名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 取信号含义
     *
     * @return 含义
     */
    public String getNote() {
        return note;
    }

    /**
     * 是否为买入方向信号
     *
     * @return 金叉或超卖返回 true
     */
    public boolean bullish() {
        return this == RSI_OVERSOLD || this == MACD_GOLDEN_CROSS || this == KDJ_GOLDEN_CROSS;
    }
}
