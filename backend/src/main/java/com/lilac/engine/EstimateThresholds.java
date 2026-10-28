package com.lilac.engine;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 预估阈值与代理标的配置，默认值取自 ISSUE-001 实测
 */
@Data
@Component
public class EstimateThresholds {

    /**
     * A 档点估计的 MAE 上限，百分点
     */
    @Value("${fund.estimate.tier.a-mae-limit:0.3}")
    private BigDecimal aMaeLimit = new BigDecimal("0.3");

    /**
     * 股票型、场内 ETF、红利、黄金的误差阈值，百分点
     */
    @Value("${fund.estimate.tier.stock-error-threshold:0.3}")
    private BigDecimal stockErrorThreshold = new BigDecimal("0.3");

    /**
     * 债券型的误差阈值，百分点；债券日波动本身极小，套用 0.3 会让阈值失去意义
     */
    @Value("${fund.estimate.tier.bond-error-threshold:0.10}")
    private BigDecimal bondErrorThreshold = new BigDecimal("0.10");

    /**
     * 判定方向命中时的涨跌下限，百分点；两侧都超过该值才比较方向
     */
    @Value("${fund.estimate.tier.direction-floor:0.05}")
    private BigDecimal directionFloor = new BigDecimal("0.05");

    /**
     * 混合型区间覆盖率下限，实际值落在区间内的比例
     */
    @Value("${fund.estimate.tier.interval-coverage-min:0.80}")
    private BigDecimal intervalCoverageMin = new BigDecimal("0.80");

    /**
     * 连续超阈值多少期后给出「建议停用该基金预估」提示
     */
    @Value("${fund.estimate.tier.suspend-consecutive-days:3}")
    private int suspendConsecutiveDays = 3;

    /**
     * B 档要求的方向准确率下限
     */
    @Value("${fund.estimate.tier.b-direction-accuracy:0.85}")
    private BigDecimal bDirectionAccuracy = new BigDecimal("0.85");

    /**
     * B 档要求的区间宽与波动之比上限，超过说明区间没有信息量
     */
    @Value("${fund.estimate.tier.b-width-ratio:1.0}")
    private BigDecimal bWidthRatio = new BigDecimal("1.0");

    /**
     * 档位判定的长窗口长度，交易日；窗口越长单日误差越难改变档位
     */
    @Value("${fund.estimate.history.window:60}")
    private int historyWindow = 60;

    /**
     * 档位判定交叉校验用的短窗口长度，交易日
     */
    @Value("${fund.estimate.history.short-window:20}")
    private int shortWindow = 20;

    /**
     * 判定档位所需的最少历史样本，不足时按类型给默认档
     */
    @Value("${fund.estimate.history.min-samples:20}")
    private int minSamples = 20;

    /**
     * 区间半宽使用的 sigma 倍数，1.64 对应正态下约 90% 覆盖率
     */
    @Value("${fund.estimate.interval.sigma-factor:1.64}")
    private BigDecimal sigmaFactor = new BigDecimal("1.64");

    /**
     * 无历史误差时的默认误差标准差，百分点；混合型实测中位约 0.30
     */
    @Value("${fund.estimate.interval.default-sigma:0.3}")
    private BigDecimal defaultSigma = new BigDecimal("0.3");

    /**
     * 有效持仓覆盖率下限，百分点；低于此值不输出数字
     */
    @Value("${fund.estimate.min-coverage:10}")
    private BigDecimal minCoverage = new BigDecimal("10");

    /**
     * 允许被剔除的持仓权重上限，百分点；超过说明数据不足，不能出数
     */
    @Value("${fund.estimate.max-excluded-weight:30}")
    private BigDecimal maxExcludedWeight = new BigDecimal("30");

    /**
     * 黄金代理标的，华安黄金 ETF
     */
    @Value("${fund.estimate.gold.proxy-code:518880}")
    private String goldProxyCode = "518880";

    /**
     * 黄金 β 的滚动窗口，交易日；取 issue 允许的 20~30 日下限，实测两只联接基金落在 0.90~0.92
     */
    @Value("${fund.estimate.gold.window:20}")
    private int goldWindow = 20;

    /**
     * 黄金 β 估计所需的最少配对样本，不足时退化为直取代理
     */
    @Value("${fund.estimate.gold.min-pairs:10}")
    private int goldMinPairs = 10;

    /**
     * 债券代理标的，国泰上证 5 年期国债 ETF
     */
    @Value("${fund.estimate.bond.proxy-code:511010}")
    private String bondProxyCode = "511010";

    /**
     * 单次预估的持仓上限，PRD 第 9 节的上限为 30 只
     */
    @Value("${fund.estimate.max-items:30}")
    private int maxItems = 30;

    /**
     * 高可信度要求的覆盖率下限，百分点
     */
    @Value("${fund.estimate.confidence.high-coverage:85}")
    private BigDecimal highCoverage = new BigDecimal("85");

    /**
     * 高可信度允许的历史 MAE，百分点
     */
    @Value("${fund.estimate.confidence.high-mae:0.2}")
    private BigDecimal highMae = new BigDecimal("0.2");

    /**
     * 中可信度要求的覆盖率下限，百分点
     */
    @Value("${fund.estimate.confidence.medium-coverage:60}")
    private BigDecimal mediumCoverage = new BigDecimal("60");

    /**
     * 中可信度允许的历史 MAE，百分点
     */
    @Value("${fund.estimate.confidence.medium-mae:0.35}")
    private BigDecimal mediumMae = new BigDecimal("0.35");

    /**
     * 预估结果缓存保留时间，秒，默认覆盖一个交易日
     */
    @Value("${fund.estimate.cache-ttl-seconds:1800}")
    private long cacheTtlSeconds = 1800;

    /**
     * 取实际净值时的回溯天数，覆盖最近一个交易日的净值公布延迟
     */
    @Value("${fund.estimate.calibration.actual-lookback-days:10}")
    private int actualLookbackDays = 10;
}
