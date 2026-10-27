package com.lilac.enums;

import lombok.Getter;

/**
 * 基金类型，对应 PRD 第 7 节要求区分的六类
 */
@Getter
public enum FundCategory {

    /**
     * 股票型
     */
    STOCK("股票型"),

    /**
     * 混合型
     */
    MIXED("混合型"),

    /**
     * 债券型
     */
    BOND("债券型"),

    /**
     * 黄金
     */
    GOLD("黄金"),

    /**
     * 红利
     */
    DIVIDEND("红利"),

    /**
     * 场内 ETF
     */
    ETF("场内 ETF"),

    /**
     * 类型未知，等待使用者手工确认
     */
    UNKNOWN("待确认");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    FundCategory(String label) {
        this.label = label;
    }
}
