package com.lilac.domain.vo;

import com.lilac.enums.ChangeDirection;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当日预估留存视图，晚间校准据此还原白天到底输出了什么
 */
@Data
public class EstimateSnapshotVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 预估所属交易日
     */
    private LocalDate tradeDate;

    /**
     * 当时基金类型
     */
    private FundCategory category;

    /**
     * 当时走的预估分支
     */
    private EstimateBranch branch;

    /**
     * 当时输出档位
     */
    private EstimateTier outputTier;

    /**
     * 当时输出的点估计，百分点；B、C 档为 null
     */
    private BigDecimal estimatedChange;

    /**
     * 当时输出的方向
     */
    private ChangeDirection direction;

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

    /**
     * 留存写入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
