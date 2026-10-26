package com.lilac.enums;

/**
 * 品种类型，与 6 位代码共同组成品种业务主键
 */
public enum VarietyType {

    /**
     * 场外基金
     */
    OF,

    /**
     * 场内 ETF
     */
    ETF,

    /**
     * 指数，不从持仓入口录入
     */
    IDX
}
