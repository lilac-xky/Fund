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
     * 是否超过该类型阈值
     */
    private Boolean overThreshold;

    /**
     * 重仓股披露期
     */
    private String disclosurePeriod;

    /**
     * 当时输出档位
     */
    private String outputTier;

    private static final long serialVersionUID = 1L;
}
