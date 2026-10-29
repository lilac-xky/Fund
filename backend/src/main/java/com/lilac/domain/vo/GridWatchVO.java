package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 网格监控对象，说明这套参数管的是哪只 ETF 以及它自己的格子
 */
@Data
public class GridWatchVO implements Serializable {

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
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 该标的的价格下界，单位元
     */
    private BigDecimal lowerBound;

    /**
     * 该标的的价格上界，单位元
     */
    private BigDecimal upperBound;

    /**
     * 该标的的格子大小，单位元
     */
    private BigDecimal step;

    /**
     * 该标的的每格金额，单位元
     */
    private BigDecimal amountPerGrid;

    /**
     * 该标的的参数是否齐全且自洽
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单，可用时为空
     */
    private List<String> paramIssues;

    /**
     * 参数提示，如「正在沿用通用参数」
     */
    private List<String> paramNotes;

    /**
     * 该标的能划出的网格线条数
     */
    private Integer lineCount;

    /**
     * 是否已单独配置；false 表示仍在沿用通用参数
     */
    private Boolean customized;

    private static final long serialVersionUID = 1L;
}
