package com.lilac.enums;

/**
 * 网格触发动作，价格下穿网格线买入、上穿网格线卖出
 */
public enum GridAction {

    /**
     * 价格下穿网格线，建议买入
     */
    BUY("买入"),

    /**
     * 价格上穿网格线，建议卖出
     */
    SELL("卖出"),

    /**
     * 未越过任何网格线，不产生提醒
     */
    NONE("未触发"),

    /**
     * 同标的有红利建议时网格让位，不给反向动作
     */
    SUPPRESSED("已让位");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    GridAction(String label) {
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
