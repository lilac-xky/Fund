package com.lilac.enums;

/**
 * 指标计算的输入口径，场内用日线行情，场外用历史净值
 */
public enum IndicatorBasis {

    /**
     * 日线行情，含开高低收量，三个指标都能算
     */
    DAILY_BAR("日线"),

    /**
     * 场外基金的单位净值序列，无最高最低价，KDJ 不可算
     */
    NET_WORTH("净值");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    IndicatorBasis(String label) {
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
}
