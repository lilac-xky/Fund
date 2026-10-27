package com.lilac.adapter.model;

/**
 * 行情可用性状态，明确区分「取到」「停牌」「无行情」「无成交」四种情形
 */
public enum QuoteStatus {

    /**
     * 可用，涨跌幅可参与预估
     */
    OK,

    /**
     * 停牌或当日无成交，不得按 0% 计入
     */
    SUSPENDED,

    /**
     * 该标的在数据源无行情（如 920xxx 北交所）
     */
    NO_DATA,

    /**
     * 数据源整体不可用，未取到任何有效响应
     */
    UNAVAILABLE
}
