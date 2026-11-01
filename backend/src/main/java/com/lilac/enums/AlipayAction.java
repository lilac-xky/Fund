package com.lilac.enums;

/**
 * 支付宝账单里识别出的基金动作类型
 */
public enum AlipayAction {

    /**
     * 买入，支出金额，按当日净值折算份额
     */
    BUY("买入"),

    /**
     * 卖出，收入金额，扣减已有份额
     */
    SELL("卖出"),

    /**
     * 现金分红，收入金额，只留档不参与持仓计算
     */
    CASH_DIVIDEND("现金分红"),

    /**
     * 转换，两只基金之间互换，只有金额没有份额，须人工补信息
     */
    CONVERT("转换"),

    /**
     * 转换转入侧，由同一条转换行派生，账单里没有独立记录
     */
    CONVERT_IN("转换转入"),

    /**
     * 退款，冲销同一交易号上的原动作
     */
    REFUND("退款"),

    /**
     * 无法归类，留给人看
     */
    UNKNOWN("未识别");

    private final String label;

    AlipayAction(String label) {
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

    /**
     * 转换转入侧交易号的后缀，与转出侧区分开，供导入层识别成对的两条
     */
    public static final String CONVERT_IN_SUFFIX = "#IN";
}
