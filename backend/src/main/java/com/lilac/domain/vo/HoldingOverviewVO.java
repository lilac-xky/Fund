package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 持仓总览视图，按品种聚合持有中持仓并带出名称与最新净值
 */
@Data
public class HoldingOverviewVO implements Serializable {

    /**
     * 持仓品种数，与 PRD 的 30 只上限对应
     */
    private Integer varietyCount;

    /**
     * 逐品种聚合行
     */
    private List<HoldingVarietyVO> items;

    /**
     * 持仓市值合计，单位元；净值或行情缺失的品种按 0 计
     */
    private BigDecimal totalMarketValue;

    /**
     * 当日收益合计，单位元；缺当日涨跌幅的品种不计入
     */
    private BigDecimal totalDailyProfitLoss;

    /**
     * 含现金分红的总收益合计，与支付宝「持有收益」同口径
     */
    private BigDecimal totalProfitLoss;

    /**
     * 成本合计，单位元
     */
    private BigDecimal totalCostAmount;

    /**
     * 含分红的总收益率合计，单位百分点
     */
    private BigDecimal totalProfitRate;

    /**
     * 汇总说明，如部分品种净值缺失
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}
