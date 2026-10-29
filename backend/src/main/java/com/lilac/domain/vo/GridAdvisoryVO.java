package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 网格提醒总览，逐只给出触发情况，参数不可用时整批不产生提醒
 */
@Data
public class GridAdvisoryVO implements Serializable {

    /**
     * 逐只提醒，按触发优先、再按建议金额降序
     */
    private List<GridAdviceVO> items;

    /**
     * 未触发的标的，逐只带原因
     */
    private List<GridAdviceVO> idleItems;

    /**
     * 本次纳入的标的数
     */
    private Integer requested;

    /**
     * 触发提醒的标的数
     */
    private Integer triggeredCount;

    /**
     * 因红利建议让位的标的数
     */
    private Integer suppressedCount;

    /**
     * 未触发的标的数
     */
    private Integer idleCount;

    /**
     * 建议买入合计，单位元
     */
    private BigDecimal totalBuyAmount;

    /**
     * 建议卖出合计，单位元
     */
    private BigDecimal totalSellAmount;

    /**
     * 参数是否可用，false 时整批不给提醒
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单
     */
    private List<String> paramIssues;

    /**
     * 与红利策略的互斥口径说明
     */
    private String dividendNote;

    /**
     * 操作提示，如无场内 ETF 持仓
     */
    private String notice;

    /**
     * 汇总摘要，供邮件直接取用
     */
    private List<String> summary;

    private static final long serialVersionUID = 1L;
}
