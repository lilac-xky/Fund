package com.lilac.strategy.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标参数，取自 strategy_param 的 INDICATOR 分组，含 RSI、KDJ、MACD 三组周期与阈值
 */
@Data
public class IndicatorPlan implements Serializable {

    /**
     * RSI 计算周期
     */
    private Integer rsiPeriod;

    /**
     * RSI 超买阈值
     */
    private BigDecimal rsiUpper;

    /**
     * RSI 超卖阈值
     */
    private BigDecimal rsiLower;

    /**
     * KDJ 的 RSV 周期
     */
    private Integer kdjPeriod;

    /**
     * KDJ 的 K 平滑系数
     */
    private Integer kdjKSmooth;

    /**
     * KDJ 的 D 平滑系数
     */
    private Integer kdjDSmooth;

    /**
     * MACD 快线周期
     */
    private Integer macdFast;

    /**
     * MACD 慢线周期
     */
    private Integer macdSlow;

    /**
     * MACD 信号线周期
     */
    private Integer macdSignal;

    /**
     * 指标信号是否参与收盘前邮件提醒
     */
    private Boolean participateInAlert;

    /**
     * 参数问题清单，空表示可用
     */
    private List<String> issues = new ArrayList<>();

    /**
     * 参数是否齐全且自洽
     *
     * @return 可用返回 true
     */
    public boolean complete() {
        return issues.isEmpty() && rsiPeriod != null && rsiUpper != null && rsiLower != null
                && kdjPeriod != null && kdjKSmooth != null && kdjDSmooth != null
                && macdFast != null && macdSlow != null && macdSignal != null;
    }

    /**
     * 指标是否参与提醒，未设置时按参与处理
     *
     * @return 参与返回 true
     */
    public boolean alertEnabled() {
        return !Boolean.FALSE.equals(participateInAlert);
    }

    private static final long serialVersionUID = 1L;
}
