package com.lilac.enums;

/**
 * 持仓变动类型
 */
public enum HoldingChangeType {

    /**
     * 买入，新增一条持仓记录
     */
    BUY,

    /**
     * 卖出，按 FIFO 扣减已有持仓
     */
    SELL
}
