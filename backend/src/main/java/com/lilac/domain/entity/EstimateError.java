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
     * 误差是否超过该基金类型的阈值
     */
    private Boolean overThreshold;

    /**
     * 当期重仓股披露期，形如 2026Q2
     */
    private String disclosurePeriod;

    /**
     * 当时输出档位，A 点估计、B 方向加区间、C 不显示
     */
    private String outputTier;
}
