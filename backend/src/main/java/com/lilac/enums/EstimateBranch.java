package com.lilac.enums;

import lombok.Getter;

/**
 * 预估分支，决定一只基金走哪条算法
 */
@Getter
public enum EstimateBranch {

    /**
     * 被动指数型与场内 ETF，直接取对标指数或自身行情
     */
    INDEX_ONLY("指数直取"),

    /**
     * 股票型与红利，全量持仓加权，残差按 0 计
     */
    STOCK_WEIGHTED("持仓加权"),

    /**
     * 黄金，黄金 ETF 涨跌乘 β 再加 AR(1) 误差反馈
     */
    GOLD_PROXY("黄金 β+AR(1)"),

    /**
     * 债券，用债券 ETF 代理，不走重仓股
     */
    BOND_PROXY("债券代理"),

    /**
     * 无法预估，不输出任何数字
     */
    UNAVAILABLE("无法预估");

    /**
     * 中文名称，供页面与接口回显
     */
    private final String label;

    EstimateBranch(String label) {
        this.label = label;
    }
}
