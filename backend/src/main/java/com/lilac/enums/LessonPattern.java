package com.lilac.enums;

/**
 * 历史错误模式，对应 PRD 附录 A 中使用者确认过的三类重复犯错
 */
public enum LessonPattern {

    /**
     * 追高被套：在高位买入后价格回落被套
     */
    CHASE_HIGH("追高被套"),

    /**
     * 盲目换仓：债券横盘后未了解品种即换入红利等不熟悉的标的
     */
    BLIND_SWITCH("盲目换仓"),

    /**
     * 恐慌卖出：持仓连续亏损时割肉
     */
    PANIC_SELL("恐慌卖出");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    LessonPattern(String label) {
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
