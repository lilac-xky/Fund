package com.lilac.enums;

import lombok.Getter;

/**
 * 输出档位，决定一只基金给数字、给方向区间还是什么都不给
 */
@Getter
public enum EstimateTier {

    /**
     * 点估计，输出预估涨跌幅数字
     */
    A("点估计"),

    /**
     * 方向加区间，不给单一数字
     */
    B("方向 + 区间"),

    /**
     * 不显示数字，也不显示方向
     */
    C("不显示");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    EstimateTier(String label) {
        this.label = label;
    }
}
