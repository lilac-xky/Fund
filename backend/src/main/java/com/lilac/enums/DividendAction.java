package com.lilac.enums;

/**
 * 红利加减仓动作，动作由股息率落区决定，金额由基准仓位与保底分红金额决定
 */
public enum DividendAction {

    /**
     * 加仓，股息率不低于加仓阈值时补到基准仓位
     */
    ADD("加仓"),

    /**
     * 减仓，股息率不高于减仓阈值时减到保底分红金额
     */
    REDUCE("减仓"),

    /**
     * 持有，股息率在阈值区间内，或已到基准仓位、已触及保底分红金额
     */
    HOLD("持有"),

    /**
     * 不给动作，股息率过期或无数据、当前金额取不到时使用
     */
    UNDECIDABLE("不给动作");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    DividendAction(String label) {
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
