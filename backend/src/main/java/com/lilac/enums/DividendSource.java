package com.lilac.enums;

/**
 * 股息率数据来源，手工录入优先于自动抓取
 */
public enum DividendSource {

    /**
     * 从中证指数官网估值文件自动抓取
     */
    AUTO("自动抓取"),

    /**
     * 使用者手工录入，国证系列只能走这条路径
     */
    MANUAL("手工覆盖");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    DividendSource(String label) {
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
