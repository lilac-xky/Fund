package com.lilac.strategy.model;

import com.lilac.enums.GridAction;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次网格判断的结论，含当前所处格子、越过的网格线与建议金额
 */
@Data
public class GridDecision {

    /**
     * 动作，买入、卖出、未触发或已让位
     */
    private GridAction action;

    /**
     * 当前价格，单位元
     */
    private BigDecimal currentPrice;

    /**
     * 当前所处格子序号，从下界那一格起算为 0；超出上下界时为 null
     */
    private Integer currentGrid;

    /**
     * 本次越过（或刚好踩到）的网格线价格，未触发时为 null
     */
    private BigDecimal crossedLine;

    /**
     * 该网格线对应的买卖金额，未触发时为 null
     */
    private BigDecimal suggestAmount;

    /**
     * 价格相对上一条网格线的偏离，单位元；用于解释为何触发
     */
    private BigDecimal distanceToLine;

    /**
     * 网格线总条数，供页面展示区间是否合适
     */
    private Integer lineCount;

    /**
     * 是否因同标的有红利建议而让位
     */
    private Boolean suppressed;

    /**
     * 判断依据文字，逐条可读
     */
    private List<String> remarks = new ArrayList<>();
}
