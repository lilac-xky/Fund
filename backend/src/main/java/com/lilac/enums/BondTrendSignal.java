package com.lilac.enums;

/**
 * 债券趋势信号，横盘或转弱到阈值即提示考虑换仓
 */
public enum BondTrendSignal {

    /**
     * 横盘达阈值，净值长期不涨
     */
    SIDEWAYS("横盘"),

    /**
     * 趋势转弱，斜率向下且波动放大
     */
    WEAK("趋势转弱"),

    /**
     * 正常，未达任一阈值
     */
    NORMAL("正常"),

    /**
     * 净值样本不足，无法判断
     */
    UNAVAILABLE("数据不足");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    BondTrendSignal(String label) {
        this.label = label;
    }

    /**
     * 取中文名称
     *
     * @return 中文名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 是否属于需要提醒换仓的信号
     *
     * @return 横盘或转弱返回 true
     */
    public boolean alerting() {
        return this == SIDEWAYS || this == WEAK;
    }
}
