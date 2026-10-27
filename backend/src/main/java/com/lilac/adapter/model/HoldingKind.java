package com.lilac.adapter.model;

/**
 * 持仓披露类型，股票走 jjcc、债券走 zqcc
 */
public enum HoldingKind {

    /**
     * 股票投资明细
     */
    STOCK("jjcc", "股票投资明细"),

    /**
     * 债券投资明细
     */
    BOND("zqcc", "债券投资明细");

    private final String type;
    private final String title;

    HoldingKind(String type, String title) {
        this.type = type;
        this.title = title;
    }

    /**
     * 上游 type 参数
     *
     * @return 接口 type 值
     */
    public String getType() {
        return type;
    }

    /**
     * 中文名称，用于异常与日志
     *
     * @return 中文名称
     */
    public String getTitle() {
        return title;
    }
}
