package com.lilac.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 一份季报或中报披露的持仓快照
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundHoldingSnapshot {

    /**
     * 报告期，如 2026Q2
     */
    private String period;

    /**
     * 持仓截止日，季末最后一个自然日
     */
    private LocalDate cutoffDate;

    /**
     * 披露类型，股票或债券
     */
    private HoldingKind kind;

    /**
     * 持仓明细，按权重降序
     */
    private List<FundHoldingItem> items;

    /**
     * 权重合计，单位百分点；季报只有前十大时明显低于股票仓位
     */
    public BigDecimal totalWeight() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(FundHoldingItem::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
