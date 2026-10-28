package com.lilac.domain.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 黄金 β 记录，逐日留存滚动 β、ρ 与残差 P90，供 β+AR(1) 估计复用与交叉校验
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "estimate_gold_beta", camelToUnderline = false, comment = "黄金 β 记录，供 β+AR(1) 估计复用与交叉校验")
public class EstimateGoldBeta extends BaseEntity {

    /**
     * 对应品种的业务主键
     */
    private String varietyKey;

    /**
     * 记录日，与校准日一致
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
     * 回归残差绝对值的 P90，百分点
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
}
