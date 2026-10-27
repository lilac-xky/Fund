package com.lilac.adapter.model;

/**
 * 交易场所身份类型，来自行情接口的类型标记
 */
public enum ExchangeKind {

    /**
     * 指数
     */
    INDEX,

    /**
     * 场内 ETF
     */
    ETF,

    /**
     * 场内 LOF，场外同样可申购，按场外基金处理
     */
    LOF,

    /**
     * 股票，本工具不录入
     */
    STOCK
}
