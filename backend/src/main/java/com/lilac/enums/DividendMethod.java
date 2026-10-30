package com.lilac.enums;

/**
 * 基金分红方式，决定分红是落成现金还是折成份额
 */
public enum DividendMethod {

    /**
     * 现金分红，累计净值与单位净值的差就是已分到账的现金
     */
    CASH("现金分红"),

    /**
     * 红利再投，分红直接折成份额，单位净值同样会掉，此时差额不能当现金
     */
    REINVEST("红利再投");

    private final String label;

    /**
     * 记录中文名
     *
     * @param label 中文名
     */
    DividendMethod(String label) {
        this.label = label;
    }

    /**
     * 取中文名
     *
     * @return 中文名
     */
    public String getLabel() {
        return label;
    }
}
