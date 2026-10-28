package com.lilac.enums;

import lombok.Getter;

/**
 * 预估涨跌方向，B 档只给方向与区间时使用
 */
@Getter
public enum ChangeDirection {

    /**
     * 看涨
     */
    UP("看涨"),

    /**
     * 看跌
     */
    DOWN("看跌"),

    /**
     * 横盘，预估涨跌为零
     */
    FLAT("横盘");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    ChangeDirection(String label) {
        this.label = label;
    }
}
