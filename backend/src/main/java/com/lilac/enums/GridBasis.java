package com.lilac.enums;

/**
 * 网格判断所用的价格口径，场内用实时价、场外用跟踪 ETF 估出的净值
 */
public enum GridBasis {

    /**
     * 场内 ETF 实时行情价，可在盘中随时判断
     */
    REALTIME_PRICE("场内实时价"),

    /**
     * 场外基金按跟踪 ETF 当日涨跌估出的净值，只在尾盘判断
     */
    ESTIMATED_NAV("尾盘预估净值");

    private final String label;

    /**
     * 记录中文名
     *
     * @param label 中文名
     */
    GridBasis(String label) {
        this.label = label;
    }

    /**
     * 取中文名
     *
     * @return 中文名
     */
    public String getLabel() {
        return label;
    }
}
