package com.lilac.domain.dto.estimate;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 误差历史保存请求
 */
@Data
public class EstimateErrorSaveRequest implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 预估涨跌幅，单位百分点
     */
    private BigDecimal estimatedChange;

    /**
     * 实际涨跌幅，单位百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际
     */
    private BigDecimal errorValue;

    /**
     * 是否超过该类型阈值，混合型为方向未命中或落在区间外
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
     * 实际值是否落在区间内，混合型统计用
     */
    private Boolean inInterval;

    /**
     * 所给区间宽度，百分点
     */
    private BigDecimal intervalWidth;

    /**
     * 重仓股披露期
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
     * 当时输出档位
     */
    private String outputTier;

    /**
     * 预估来源，SNAPSHOT 取当日留存、RECOMPUTED 事后重算
     */
    private String estimateSource;

    private static final long serialVersionUID = 1L;
}
