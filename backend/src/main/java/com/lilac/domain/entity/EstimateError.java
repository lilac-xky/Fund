package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 误差历史，记录每个交易日预估与实际净值的对照结果
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "estimate_error", camelToUnderline = false, comment = "误差历史，每个交易日预估与实际净值的对照结果")
public class EstimateError extends BaseEntity {

    /**
     * 对应品种的业务主键
     */
    private String varietyKey;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 当日预估涨跌幅，单位为百分点
     */
    private BigDecimal estimatedChange;

    /**
     * 当日实际净值涨跌幅，单位为百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际，单位为百分点
     */
    private BigDecimal errorValue;

    /**
     * 误差是否超过该基金类型的阈值，混合型为方向未命中或落在区间外
     */
    private Boolean overThreshold;

    /**
     * 本次判定所用阈值，百分点；混合型不参与点估计判定，为 null
     */
    private BigDecimal thresholdValue;

    /**
     * 方向是否命中，混合型统计用
     */
    private Boolean directionHit;

    /**
     * 实际值是否落在所给区间内，混合型统计用
     */
    private Boolean inInterval;

    /**
     * 所给区间宽度，百分点，混合型统计用
     */
    private BigDecimal intervalWidth;

    /**
     * 当期重仓股披露期，形如 2026Q2
     */
    private String disclosurePeriod;

    /**
     * 当时持仓截止日
     */
    private LocalDate holdingCutoffDate;

    /**
     * 当时持仓披露日
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 当时输出档位，A 点估计、B 方向加区间、C 不显示
     */
    private String outputTier;

    /**
     * 预估来源，SNAPSHOT 取当日留存、RECOMPUTED 事后重算
     */
    private String estimateSource;
}
