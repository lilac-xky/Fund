package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 复盘报告的一行，一只基金的预估与实际对照
 */
@Data
public class ReportRowVO implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 输出档位
     */
    private String outputTier;

    /**
     * 预估涨跌幅，百分点；无点估计为空
     */
    private BigDecimal estimatedChange;

    /**
     * 实际涨跌幅，百分点
     */
    private BigDecimal actualChange;

    /**
     * 误差，预估减实际，百分点
     */
    private BigDecimal errorValue;

    /**
     * 本次判定所用阈值，百分点
     */
    private BigDecimal thresholdValue;

    /**
     * 是否超阈值
     */
    private Boolean overThreshold;

    /**
     * 方向是否命中
     */
    private Boolean directionHit;

    /**
     * 状态说明，如未校准的原因
     */
    private String status;

    private static final long serialVersionUID = 1L;
}
