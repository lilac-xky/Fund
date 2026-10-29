package com.lilac.domain.vo;

import com.lilac.enums.GridAction;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只标的的网格到价提醒，含当前格子、穿越的网格线与建议金额
 */
@Data
public class GridAdviceVO implements Serializable {

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
     * 动作，买入、卖出、未触发或已让位
     */
    private GridAction action;

    /**
     * 动作中文名
     */
    private String actionLabel;

    /**
     * 当前价，单位元
     */
    private BigDecimal currentPrice;

    /**
     * 当前价的数据日期
     */
    private LocalDate priceDate;

    /**
     * 当前价的取数说明，如「行情价（实时）」
     */
    private String priceSource;

    /**
     * 参考价，判断穿越方向的基准，一般为上一交易日收盘价
     */
    private BigDecimal referencePrice;

    /**
     * 参考价的数据日期
     */
    private LocalDate referenceDate;

    /**
     * 当前所处格子序号，从下界起算为 0；超出区间为 null
     */
    private Integer currentGrid;

    /**
     * 本次越过的网格线价格，未触发为 null
     */
    private BigDecimal crossedLine;

    /**
     * 建议买卖金额，单位元，未触发为 null
     */
    private BigDecimal suggestAmount;

    /**
     * 网格线总条数
     */
    private Integer lineCount;

    /**
     * 网格下界
     */
    private BigDecimal lowerBound;

    /**
     * 网格上界
     */
    private BigDecimal upperBound;

    /**
     * 每格金额
     */
    private BigDecimal amountPerGrid;

    /**
     * 是否因同标的有红利建议而让位
     */
    private Boolean suppressed;

    /**
     * 判断依据文字，逐条可读
     */
    private List<String> remarks;

    private static final long serialVersionUID = 1L;
}
