package com.lilac.enums;

/**
 * 基金类型，对应 PRD 第 7 节要求区分的六类
 */
public enum FundCategory {

    /**
     * 股票型
     */
    STOCK,

    /**
     * 混合型
     */
    MIXED,

    /**
     * 债券型
     */
    BOND,

    /**
     * 黄金
     */
    GOLD,

    /**
     * 红利
     */
    DIVIDEND,

    /**
     * 场内 ETF
     */
    ETF,

    /**
     * 类型未知，等待使用者手工确认
     */
    UNKNOWN
}
