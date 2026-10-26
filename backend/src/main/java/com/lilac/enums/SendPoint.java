package com.lilac.enums;

/**
 * 每日发送时点，对应四个定时产出时间
 */
public enum SendPoint {

    /**
     * 早盘，约 9:30
     */
    MORNING,

    /**
     * 午盘，约 11:30 至 13:00
     */
    NOON,

    /**
     * 收盘前 30 分钟，约 14:30
     */
    PRE_CLOSE,

    /**
     * 晚间净值校准，约 20:00 至 22:00
     */
    EVENING
}
