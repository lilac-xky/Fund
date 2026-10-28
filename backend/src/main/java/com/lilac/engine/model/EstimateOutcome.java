package com.lilac.engine.model;

import com.lilac.enums.ChangeDirection;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一只基金的预估结果，含档位、可信度与不可预估原因
 */
@Data
public class EstimateOutcome {

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
     * 本次实际走了哪条分支
     */
    private EstimateBranch branch;

    /**
     * 输出档位
     */
    private EstimateTier tier;

    /**
     * 可信度
     */
    private EstimateConfidence confidence;

    /**
     * 预估涨跌幅，百分点；B 档与 C 档为 null，不对外输出数字
     */
    private BigDecimal estimatedChange;

    /**
     * 涨跌方向，B 档与 C 档使用
     */
    private ChangeDirection direction;

    /**
     * 区间下限，百分点，仅 B 档
     */
    private BigDecimal intervalLow;

    /**
     * 区间上限，百分点，仅 B 档
     */
    private BigDecimal intervalHigh;

    /**
     * 参与加权的持仓覆盖率，百分点；非持仓分支为 null
     */
    private BigDecimal coverage;

    /**
     * 报告期，如 2026Q2
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
     * 行情数据日期，所有标的都取不到时为 null
     */
    private LocalDate quoteDate;

    /**
     * 行情是否交易日内的实时数据
     */
    private Boolean realtime;

    /**
     * 代理标的完整代码，如 sh000300
     */
    private String proxySymbol;

    /**
     * 黄金 β，非黄金分支为 null
     */
    private BigDecimal beta;

    /**
     * 黄金 AR(1) 系数 ρ，非黄金分支为 null
     */
    private BigDecimal rho;

    /**
     * 黄金残差 P90，百分点；供 β+AR(1) 估计的交叉校验与校准留存
     */
    private BigDecimal residualP90;

    /**
     * 是否走降级缓存
     */
    private Boolean degraded;

    /**
     * 不可预估的原因，可预估时为 null
     */
    private String unavailableReason;

    /**
     * 不可预估的细节，如停牌标的名单
     */
    private String unavailableDetail;

    /**
     * 需要在页面展示的说明，每条一句
     */
    private List<String> remarks = new ArrayList<>();

    /**
     * 是否给了可对外输出，A 档有数字、B 档有方向都算
     *
     * @return 可对外输出返回 true
     */
    public boolean estimable() {
        if (unavailableReason != null) {
            return false;
        }
        return estimatedChange != null || direction != null;
    }

    /**
     * 标记为不可预估，清空所有数字
     *
     * @param reason 原因
     * @param detail 细节，可为 null
     */
    public void markUnavailable(String reason, String detail) {
        this.unavailableReason = reason;
        this.unavailableDetail = detail;
        this.estimatedChange = null;
        this.intervalLow = null;
        this.intervalHigh = null;
        this.direction = null;
    }
}
