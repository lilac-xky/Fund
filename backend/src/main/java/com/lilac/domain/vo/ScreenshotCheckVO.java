package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 截图与已录入持仓的核对结果，逐只标出差异
 */
@Data
public class ScreenshotCheckVO implements Serializable {

    /**
     * 留存的截图
     */
    private ScreenshotVO screenshot;

    /**
     * 逐只核对行
     */
    private List<CheckRow> rows;

    /**
     * 差异条数
     */
    private Integer diffCount;

    /**
     * 核对说明
     */
    private String notice;

    private static final long serialVersionUID = 1L;

    /**
     * 一只基金的核对结果
     */
    @Data
    public static class CheckRow implements Serializable {

        /**
         * 品种业务主键
         */
        private String varietyKey;

        /**
         * 6 位代码
         */
        private String varietyCode;

        /**
         * 标的名称
         */
        private String varietyName;

        /**
         * 已录入的持有份额
         */
        private java.math.BigDecimal shares;

        /**
         * 已录入的加权成本价
         */
        private java.math.BigDecimal avgCostPrice;

        /**
         * 已录入的市值
         */
        private java.math.BigDecimal marketValue;

        /**
         * 核对状态，如「待人工核对」
         */
        private String status;

        /**
         * 提示，说明哪些数字需要与截图比对
         */
        private List<String> hints;

        private static final long serialVersionUID = 1L;
    }
}
