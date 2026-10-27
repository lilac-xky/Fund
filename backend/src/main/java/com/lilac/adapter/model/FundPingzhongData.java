package com.lilac.adapter.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 天天基金 pingzhongdata 解析结果，只保留净值序列与季末仓位
 */
@Data
public class FundPingzhongData {

    /**
     * 6 位基金代码
     */
    private String code;

    /**
     * 基金简称
     */
    private String name;

    /**
     * 历史净值序列，按日期升序
     */
    private List<FundNetWorthPoint> netWorthPoints;

    /**
     * 季末仓位快照，按报告期升序，货币基金等无股票仓位时为空
     */
    private List<FundAssetAllocationPoint> assetAllocations;

    /**
     * 最新净值日期，取序列中的最大日期而不是最后一项，避免上游乱序时取错
     *
     * @return 净值数据日期，无净值时为 null
     */
    public LocalDate latestNetWorthDate() {
        if (netWorthPoints == null || netWorthPoints.isEmpty()) {
            return null;
        }
        return netWorthPoints.stream()
                .map(FundNetWorthPoint::getNetWorthDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * 最新一条季末仓位，按报告期取最大
     *
     * @return 仓位快照，无仓位披露时为 null
     */
    public FundAssetAllocationPoint latestAssetAllocation() {
        if (assetAllocations == null || assetAllocations.isEmpty()) {
            return null;
        }
        return assetAllocations.stream()
                .max((left, right) -> left.getReportDate().compareTo(right.getReportDate()))
                .orElse(null);
    }
}
