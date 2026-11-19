package com.lilac.enums;

/**
 * 每日发送时点，对应四个定时产出时间
 */
public enum SendPoint {

    /**
     * 早盘，约 9:30
     */
    MORNING("早盘", "早盘预估速览"),

    /**
     * 午盘，约 11:30 至 13:00
     */
    NOON("午盘", "午盘预估速览"),

    /**
     * 收盘前 30 分钟，约 14:30
     */
    PRE_CLOSE("收盘前", "收盘前提醒"),

    /**
     * 晚间净值校准，约 20:00 至 22:00
     */
    EVENING("晚间", "晚间净值校准");

    /**
     * 中文名称，供页面与导出回显
     */
    private final String label;

    /**
     * 邮件主题里用的时点名，四个时点各发一封时靠它区分
     */
    private final String mailTitle;

    SendPoint(String label, String mailTitle) {
        this.label = label;
        this.mailTitle = mailTitle;
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
     * 取邮件主题里的时点名
     *
     * @return 时点名
     */
    public String getMailTitle() {
        return mailTitle;
    }
}
