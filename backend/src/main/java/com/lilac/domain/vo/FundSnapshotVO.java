package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金净值与持仓视图，带出三个日期与可预估判定
 */
@Data
public class FundSnapshotVO implements Serializable {

    /**
     * 6 位基金代码
     */
    private String code;

    /**
     * 基金简称
     */
    private String name;

    /**
     * 净值数据日期
     */
    private LocalDate netWorthDate;

    /**
     * 持仓截止日
     */
    private LocalDate holdingCutoffDate;

    /**
     * 持仓披露日
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 报告期，如 2026Q2
     */
    private String holdingPeriod;

    /**
     * 重仓股列表，按权重降序
     */
    private List<HoldingItemVO> stockHoldings;

    /**
     * 债券持仓列表
     */
    private List<HoldingItemVO> bondHoldings;

    /**
     * 重仓股合计占比，单位百分点；季报只有前十大时明显低于股票仓位
     */
    private BigDecimal totalStockWeight;

    /**
     * 债券持仓合计占比，单位百分点
     */
    private BigDecimal totalBondWeight;

    /**
     * 是否全量披露
     */
    private Boolean fullDisclosure;

    /**
     * 季末股票占净比
     */
    private BigDecimal stockRatio;

    /**
     * 季末债券占净比
     */
    private BigDecimal bondRatio;

    /**
     * 季末现金占净比
     */
    private BigDecimal cashRatio;

    /**
     * 是否可用于当日预估
     */
    private Boolean estimable;

    /**
     * 不可预估的具体原因，可预估时为 null
     */
    private String unavailableReason;

    /**
     * 是否走了降级缓存
     */
    private Boolean degraded;

    /**
     * 降级时的数据截止时间
     */
    private LocalDate dataCutoffDate;

    /**
     * 净值序列，按日期升序
     */
    private List<NetWorthPointVO> netWorthPoints;

    private static final long serialVersionUID = 1L;
}
