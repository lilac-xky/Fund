package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 当日预估留存，把白天真正输出的结果留下来，供晚间校准与误差追溯
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "estimate_snapshot", camelToUnderline = false, comment = "当日预估留存，供晚间校准与误差追溯")
public class EstimateSnapshot extends BaseEntity {

    /**
     * 对应品种的业务主键
     */
    private String varietyKey;

    /**
     * 预估所属交易日
     */
    private LocalDate tradeDate;

    /**
     * 当时基金类型
     */
    private String category;

    /**
     * 当时走的预估分支
     */
    private String branch;

    /**
     * 当时输出档位
     */
    private String outputTier;

    /**
     * 当时输出的点估计，百分点；B、C 档为 null
     */
    private BigDecimal estimatedChange;

    /**
     * 当时输出的方向，UP / DOWN / FLAT
     */
    private String direction;

    /**
     * 当时区间下限，百分点
     */
    private BigDecimal intervalLow;

    /**
     * 当时区间上限，百分点
     */
    private BigDecimal intervalHigh;

    /**
     * 当时持仓覆盖率，百分点
     */
    private BigDecimal coverage;

    /**
     * 当时是否给出了可对照输出
     */
    private Boolean evaluable;

    /**
     * 当时不可预估的原因
     */
    private String unavailableReason;

    /**
     * 当时持仓报告期
     */
    private String holdingPeriod;

    /**
     * 当时持仓截止日
     */
    private LocalDate holdingCutoffDate;

    /**
     * 当时持仓披露日
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 当时黄金 β
     */
    private BigDecimal beta;

    /**
     * 当时黄金 AR(1) 系数
     */
    private BigDecimal rho;

    /**
     * 当时黄金残差 P90，百分点
     */
    private BigDecimal residualP90;

    /**
     * 当时行情数据日期
     */
    private LocalDate quoteDate;

    /**
     * 当时数据截止日期
     */
    private LocalDate dataCutoffDate;
}
