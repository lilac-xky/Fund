package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一次股息率刷新结果，逐只给出写入、跳过与失败原因
 */
@Data
public class IndexDividendRefreshVO implements Serializable {

    /**
     * 刷新日期
     */
    private LocalDate refreshDate;

    /**
     * 成功写入的指数数量
     */
    private Integer savedCount;

    /**
     * 自动抓取不可用、需要手工覆盖的指数数量
     */
    private Integer manualRequiredCount;

    /**
     * 明细，逐只带结果与原因
     */
    private List<Item> items;

    private static final long serialVersionUID = 1L;

    /**
     * 单只指数的刷新明细
     */
    @Data
    public static class Item implements Serializable {

        /**
         * 指数代码
         */
        private String indexCode;

        /**
         * 指数中文全称
         */
        private String indexName;

        /**
         * 数据日期
         */
        private LocalDate dataDate;

        /**
         * 股息率，单位百分点
         */
        private BigDecimal dividendYield;

        /**
         * 是否写入成功
         */
        private Boolean saved;

        /**
         * 是否需要手工覆盖，自动抓取取不到时为 true
         */
        private Boolean manualRequired;

        /**
         * 结果说明，失败与跳过时给出原因
         */
        private String message;

        private static final long serialVersionUID = 1L;
    }
}
