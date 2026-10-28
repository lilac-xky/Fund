package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 红利加减仓建议总览，逐只给出动作与金额，参数不可用时整体不给动作
 */
@Data
public class DividendAdvisoryVO implements Serializable {

    /**
     * 逐只建议，按建议金额降序
     */
    private List<DividendAdviceVO> items;

    /**
     * 无建议的品种，逐只带原因
     */
    private List<DividendAdviceVO> skippedItems;

    /**
     * 本次纳入的品种数
     */
    private Integer requested;

    /**
     * 给出加减仓动作的品种数
     */
    private Integer actionCount;

    /**
     * 维持持有的品种数
     */
    private Integer holdCount;

    /**
     * 不给动作的品种数
     */
    private Integer undecidableCount;

    /**
     * 建议买入合计，单位元
     */
    private BigDecimal totalAddAmount;

    /**
     * 建议卖出合计，单位元
     */
    private BigDecimal totalReduceAmount;

    /**
     * 当前红利持仓合计金额，单位元
     */
    private BigDecimal totalCurrentAmount;

    /**
     * 参数是否可用，false 时整批不给动作
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单
     */
    private List<String> paramIssues;

    /**
     * 基准仓位金额，单位元
     */
    private BigDecimal baseAmount;

    /**
     * 保底分红金额，单位元
     */
    private BigDecimal floorAmount;

    /**
     * 加仓股息率阈值，单位百分点
     */
    private BigDecimal addYield;

    /**
     * 减仓股息率阈值，单位百分点
     */
    private BigDecimal reduceYield;

    /**
     * 最新股息率数据日期，页面与邮件必须显示
     */
    private LocalDate dataDate;

    /**
     * 数据最旧已过天数
     */
    private Long ageDays;

    /**
     * 是否存在过期数据
     */
    private Boolean hasStale;

    /**
     * 与网格策略的互斥口径说明
     */
    private String gridNote;

    /**
     * 操作提示，如无红利持仓
     */
    private String notice;

    /**
     * 汇总里的动作分布说明，供邮件直接取用
     */
    private List<String> summary;

    private static final long serialVersionUID = 1L;
}
