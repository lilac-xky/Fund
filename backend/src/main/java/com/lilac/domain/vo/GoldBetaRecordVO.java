package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 黄金 β 记录视图
 */
@Data
public class GoldBetaRecordVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 记录日
     */
    private LocalDate tradeDate;

    /**
     * 对黄金 ETF 的滚动 β
     */
    private BigDecimal beta;

    /**
     * 回归截距，百分点
     */
    private BigDecimal alpha;

    /**
     * AR(1) 误差系数 ρ
     */
    private BigDecimal rho;

    /**
     * 残差绝对值的 P90，百分点
     */
    private BigDecimal residualP90;

    /**
     * 参与估计的配对样本数
     */
    private Integer samples;

    /**
     * 是否因样本不足退化为代理直取
     */
    private Boolean fallback;

    /**
     * 写入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
