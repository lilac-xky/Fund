package com.lilac.adapter.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金净值与持仓的取数结果，带出三个日期与降级标记
 */
@Data
public class FundSnapshot {

    /**
     * 6 位基金代码
     */
    private String code;

    /**
     * 基金简称
     */
    private String name;

    /**
     * 净值数据日期，即最近一个已公布净值的交易日
     */
    private LocalDate netWorthDate;

    /**
     * 持仓截止日，即所用报告期的季末日期
     */
    private LocalDate holdingCutoffDate;

    /**
     * 持仓披露日，披露日之后该快照才可用
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 报告期，如 2026Q2，无持仓时为 null
     */
    private String holdingPeriod;

    /**
     * 重仓股明细，按权重降序；季报只有前十大，中报与年报才是全量
     */
    private List<FundHoldingItem> stockHoldings;

    /**
     * 债券持仓明细，债券型基金才有
     */
    private List<FundHoldingItem> bondHoldings;

    /**
     * 是否全量披露，中报与年报为 true，季报只有前十大时为 false
     */
    private Boolean fullDisclosure;

    /**
     * 股票、债券、现金占净比，取自季末仓位快照
     */
    private FundAssetAllocationPoint assetAllocation;

    /**
     * 数据是否来自降级缓存，为 true 时持仓与净值可能已过期
     */
    private Boolean degraded;

    /**
     * 降级时的数据截止时间，未降级为 null
     */
    private LocalDate dataCutoffDate;

    /**
     * 重仓股合计占比，单位百分点
     *
     * @return 合计权重
     */
    public BigDecimal totalStockWeight() {
        return totalWeight(stockHoldings);
    }

    /**
     * 债券持仓合计占比，单位百分点
     *
     * @return 合计权重
     */
    public BigDecimal totalBondWeight() {
        return totalWeight(bondHoldings);
    }

    /**
     * 是否可用于当日预估，缺净值或缺股票持仓时为 false
     *
     * @return 可预估返回 true
     */
    public boolean estimable() {
        return netWorthDate != null && stockHoldings != null && !stockHoldings.isEmpty();
    }

    /**
     * 汇总明细权重，列表为空时返回 0
     *
     * @param items 明细
     * @return 合计权重
     */
    private BigDecimal totalWeight(List<FundHoldingItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream().map(FundHoldingItem::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
