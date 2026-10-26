package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 误差历史视图对象
 */
@Data
public class EstimateErrorVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 预估涨跌幅
     */
    private BigDecimal estimatedChange;

    /**
     * 实际涨跌幅
     */
    private BigDecimal actualChange;

    /**
     * 误差
     */
    private BigDecimal errorValue;

    /**
     * 是否超阈值
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
