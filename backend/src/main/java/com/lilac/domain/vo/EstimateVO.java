package com.lilac.domain.vo;

import com.lilac.enums.ChangeDirection;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单只基金的预估结果，带出档位、可信度与全部依据日期
 */
@Data
public class EstimateVO implements Serializable {

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
     * 基金类型
     */
    private FundCategory category;

    /**
     * 基金类型中文名
     */
    private String categoryLabel;

    /**
     * 本次走的分支
     */
    private EstimateBranch branch;

    /**
     * 分支中文名
     */
    private String branchLabel;

    /**
     * 输出档位
     */
    private EstimateTier tier;

    /**
     * 档位中文名
     */
    private String tierLabel;

    /**
     * 可信度
     */
    private EstimateConfidence confidence;

    /**
     * 可信度中文名
     */
    private String confidenceLabel;

    /**
     * 预估涨跌幅，百分点；仅 A 档有值，B 档与 C 档为 null
     */
    private BigDecimal estimatedChange;

    /**
     * 涨跌方向，B 档与部分 C 档使用
     */
    private ChangeDirection direction;

    /**
     * 方向中文名
     */
    private String directionLabel;

    /**
     * 区间下限，百分点，仅 B 档
     */
    private BigDecimal intervalLow;

    /**
     * 区间上限，百分点，仅 B 档
     */
    private BigDecimal intervalHigh;

    /**
     * 参与加权的覆盖率，百分点
     */
    private BigDecimal coverage;

    /**
     * 持仓报告期
     */
    private String holdingPeriod;

    /**
     * 持仓截止日
     */
    private LocalDate holdingCutoffDate;

    /**
     * 持仓披露日
     */
    private LocalDate holdingDisclosureDate;

    /**
     * 净值数据日期
     */
    private LocalDate netWorthDate;

    /**
     * 行情数据日期
     */
    private LocalDate quoteDate;

    /**
     * 行情是否来自交易时段内
     */
    private Boolean realtime;

    /**
     * 代理标的完整代码
     */
    private String proxySymbol;

    /**
     * 黄金 β
     */
    private BigDecimal beta;

    /**
     * 黄金 AR(1) 系数
     */
    private BigDecimal rho;

    /**
     * 是否来自降级缓存
     */
    private Boolean degraded;

    /**
     * 是否可预估
     */
    private Boolean estimable;

    /**
     * 不可预估原因，可预估时为 null
     */
    private String unavailableReason;

    /**
     * 不可预估细节，如停牌标的名单
     */
    private String unavailableDetail;

    /**
     * 同屏说明文字，每条一句
     */
    private List<String> remarks;

    private static final long serialVersionUID = 1L;
}
