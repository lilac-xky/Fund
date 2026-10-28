package com.lilac.domain.vo;

import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.VarietyType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓总览的单品种聚合行
 */
@Data
public class HoldingVarietyVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 品种类型
     */
    private VarietyType varietyType;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型，UNKNOWN 表示待确认
     */
    private FundCategory category;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 类型是否已确认
     */
    private Boolean confirmed;

    /**
     * 合计持有份额，同一品种多条持仓合并
     */
    private BigDecimal totalShares;

    /**
     * 加权平均成本价，按份额加权
     */
    private BigDecimal avgCostPrice;

    /**
     * 最新单位净值，场内 ETF 用最新行情价；取不到为 null
     */
    private BigDecimal latestPrice;

    /**
     * 最新净值或行情的日期
     */
    private LocalDate latestPriceDate;

    /**
     * 最新价的取数说明，如「净值 2026-10-27」或「行情价（实时）」
     */
    private String priceSource;

    /**
     * 最新一条持仓记录的建仓日期
     */
    private LocalDate lastOpenDate;

    /**
     * 持仓状态，全部记录 CLOSED 时为 CLOSED
     */
    private HoldingStatus status;

    /**
     * 持仓市值，份额乘最新价；最新价缺失时为 null
     */
    private BigDecimal marketValue;

    private static final long serialVersionUID = 1L;
}
