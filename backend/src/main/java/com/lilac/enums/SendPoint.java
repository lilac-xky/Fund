package com.lilac.enums;

/**
 * 每日发送时点，对应四个定时产出时间
 */
public enum SendPoint {

    /**
     * 早盘，约 9:30
     */
    MORNING("早盘"),

    /**
     * 午盘，约 11:30 至 13:00
     */
    NOON("午盘"),

    /**
     * 收盘前 30 分钟，约 14:30
     */
    PRE_CLOSE("收盘前"),

    /**
     * 晚间净值校准，约 20:00 至 22:00
     */
    EVENING("晚间");

    /**
     * 中文名称，供页面与导出回显
     */
    private final String label;

    SendPoint(String label) {
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
