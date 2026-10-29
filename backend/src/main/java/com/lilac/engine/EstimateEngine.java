package com.lilac.engine;

import com.lilac.adapter.model.MarketQuote;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.engine.model.EstimateContext;
import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.engine.model.EstimateOutcome;
import com.lilac.engine.model.GoldBetaModel;
import com.lilac.engine.model.WeightedOutcome;
import com.lilac.enums.ChangeDirection;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预估引擎，按基金类型走指数直取、持仓加权、黄金 β+AR(1)、债券代理四条分支
 */
@Slf4j
@Component
public class EstimateEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal FLAT_THRESHOLD = new BigDecimal("0.05");
    private static final int RESULT_SCALE = 4;
    private static final String DISCLAIMER = "预估基于重仓股与指数推算，与实际净值存在误差";
    private static final String MIXED_NOTE = "混合型主动调仓频繁，仅方向参考";
    private static final String NEW_FUND = "新基金无历史净值，暂无法预估";
    private static final String NO_HOLDING = "无已披露的股票持仓，暂无法预估";
    private static final String NO_SNAPSHOT = "净值与持仓取不到，今日无法预估";
    private static final String QUOTE_MISSING = "行情取不到，今日无法预估";
    private static final String SUSPENDED = "重仓股停牌，今日无法预估";
    private static final String LOW_COVERAGE = "可用持仓覆盖率不足，暂无法预估";
    private static final String HIGH_EXCLUSION = "重仓股停牌或缺行情过多，暂无法预估";
    private static final String UNKNOWN_CATEGORY = "基金类型待确认，请先确认类型";
    private static final String DIRECTIONS_WEAK = "方向准确率连续偏低，已降级为无法预估";
    private static final String HK_NOTE = "港股持仓已按港币兑人民币汇率折算";
    private static final String BOND_NOTE = "债券型用债券 ETF 代理，误差阈值单独分档";
    private static final String GOLD_DIRECT = "黄金 β 样本不足，已退化为代理直取";
    private static final String INTERVAL_CROSS_ZERO = "预估区间跨零，方向本身不确定，仅供参考";
    private static final String LOW_COVERAGE_NOTE = "持仓覆盖率偏低，预估误差可能放大";
    private static final String LINK_FUND_KEYWORD = "联接";
    private static final String LINK_FUND_NOTE = "ETF 联接基金按所跟踪指数估算，个股权重未参与加权";

    private final HoldingWeightCalculator holdingWeightCalculator;
    private final TierResolver tierResolver;
    private final ConfidenceResolver confidenceResolver;
    private final ErrorHistoryCalculator errorHistoryCalculator;
    private final EstimateThresholds thresholds;

    /**
     * 注入加权、档位、可信度与误差统计组件
     *
     * @param holdingWeightCalculator 持仓加权
     * @param tierResolver            档位判定
     * @param confidenceResolver      可信度判定
     * @param errorHistoryCalculator  误差统计
     * @param thresholds              阈值配置
     */
    public EstimateEngine(HoldingWeightCalculator holdingWeightCalculator, TierResolver tierResolver,
                          ConfidenceResolver confidenceResolver, ErrorHistoryCalculator errorHistoryCalculator,
                          EstimateThresholds thresholds) {
        this.holdingWeightCalculator = holdingWeightCalculator;
        this.tierResolver = tierResolver;
        this.confidenceResolver = confidenceResolver;
        this.errorHistoryCalculator = errorHistoryCalculator;
        this.thresholds = thresholds;
    }

    /**
     * 计算一只基金的预估结果，任何取不到数据的场景都走不可预估分支而不输出数字
     *
     * @param context 预估输入
     * @return 预估结果
     */
    public EstimateOutcome estimate(EstimateContext context) {
        EstimateOutcome outcome = new EstimateOutcome();
        outcome.setVarietyKey(context.getVariety().getVarietyKey());
        outcome.setVarietyCode(context.getVariety().getVarietyCode());
        outcome.setVarietyName(context.getVariety().getVarietyName());
        outcome.setCategory(context.getVariety().getCategory());
        outcome.setProxySymbol(context.getProxySymbol());
        outcome.setDegraded(context.isDegraded());
        FundCategory category = context.getVariety().getCategory();
        if (category == null || category == FundCategory.UNKNOWN) {
            outcome.setBranch(EstimateBranch.UNAVAILABLE);
            unavailable(outcome, UNKNOWN_CATEGORY, null);
            return outcome;
        }
        EstimateBranch branch = resolveBranch(context, category);
        outcome.setBranch(branch);
        switch (branch) {
            case INDEX_ONLY -> estimateByQuote(context, outcome, DISCLAIMER);
            case GOLD_PROXY -> estimateByGold(context, outcome);
            case BOND_PROXY -> estimateByQuote(context, outcome, BOND_NOTE);
            case STOCK_WEIGHTED -> estimateByHoldings(context, outcome);
            default -> {
                outcome.setBranch(EstimateBranch.UNAVAILABLE);
                unavailable(outcome, QUOTE_MISSING, null);
            }
        }
        return outcome;
    }

    /**
     * 该基金是否按 ETF 联接处理，联接基金的季报不披露个股，只能按指数估算
     *
     * @param context 预估输入
     * @return 联接基金返回 true
     */
    private boolean isLinkFund(EstimateContext context) {
        String name = context.getVariety().getVarietyName();
        return name != null && name.contains(LINK_FUND_KEYWORD);
    }

    /**
     * 判断走哪条分支：场内 ETF 与被动指数型指数直取，黄金走 β 分支，债券走代理，其余持仓加权
     *
     * @param context  预估输入
     * @param category 基金类型
     * @return 分支
     */
    private EstimateBranch resolveBranch(EstimateContext context, FundCategory category) {
        if (category == FundCategory.GOLD) {
            return EstimateBranch.GOLD_PROXY;
        }
        if (category == FundCategory.BOND) {
            return EstimateBranch.BOND_PROXY;
        }
        if (context.getVariety().getVarietyType() == VarietyType.ETF) {
            return EstimateBranch.INDEX_ONLY;
        }
        String rawType = context.getRawFundType() == null ? "" : context.getRawFundType();
        boolean passive = rawType.startsWith("指数型");
        // ETF 联接的季报里只有那只 ETF 与现金，个股权重合计不到 1%，持仓加权必然判覆盖率不足
        boolean linkFund = isLinkFund(context);
        // 红利按 ISSUE-001 实测用全量持仓加权更稳，但联接基金没有可加权的个股，只能走指数直取
        boolean dividendByIndex = category == FundCategory.DIVIDEND && linkFund;
        if (passive && (category != FundCategory.DIVIDEND || dividendByIndex)
                && context.getProxySymbol() != null) {
            return EstimateBranch.INDEX_ONLY;
        }
        return EstimateBranch.STOCK_WEIGHTED;
    }

    /**
     * 用单个标的行情直接出数，指数直取与债券代理共用
     *
     * @param context   预估输入
     * @param outcome   输出
     * @param extraNote 附加说明
     */
    private void estimateByQuote(EstimateContext context, EstimateOutcome outcome, String extraNote) {
        MarketQuote quote = context.getProxyQuote();
        if (quote == null || !quote.usable()) {
            unavailable(outcome, QUOTE_MISSING, context.getProxyUnavailableReason());
            return;
        }
        EstimateHistoryStats stats = stats(context);
        outcome.setTier(tierResolver.resolve(outcome.getCategory(), stats, recentStats(context),
                context.getPreviousTier(), thresholds));
        outcome.setConfidence(confidenceResolver.resolve(HUNDRED, stats, context.isDegraded(), thresholds));
        outcome.setQuoteDate(quote.getTradeDate());
        outcome.setRealtime(quote.getRealtime());
        // 指数直取与代理分支用整只基金作单位，覆盖率按满额给出
        outcome.setCoverage(HUNDRED);
        fillSnapshotDates(context, outcome);
        fillAllocationDate(context, outcome);
        fillPointOrTier(outcome, scale(quote.getChangePercent()), stats);
        outcome.getRemarks().add(extraNote);
        // 联接基金必须说明个股没有参与加权，否则使用者会以为这只数是用重仓股算的
        if (isLinkFund(context)) {
            outcome.getRemarks().add(LINK_FUND_NOTE);
        }
    }

    /**
     * 黄金分支，黄金 ETF 涨跌乘 β 加 AR(1) 误差反馈
     *
     * @param context 预估输入
     * @param outcome 输出
     */
    private void estimateByGold(EstimateContext context, EstimateOutcome outcome) {
        MarketQuote quote = context.getProxyQuote();
        if (quote == null || !quote.usable()) {
            unavailable(outcome, QUOTE_MISSING, context.getProxyUnavailableReason());
            return;
        }
        GoldBetaModel model = context.getGoldModel() == null ? directModel() : context.getGoldModel();
        EstimateHistoryStats stats = stats(context);
        outcome.setTier(tierResolver.resolve(outcome.getCategory(), stats, recentStats(context),
                context.getPreviousTier(), thresholds));
        outcome.setConfidence(confidenceResolver.resolve(null, stats, context.isDegraded(), thresholds));
        outcome.setQuoteDate(quote.getTradeDate());
        outcome.setRealtime(quote.getRealtime());
        outcome.setBeta(model.getBeta());
        outcome.setRho(model.getRho());
        outcome.setResidualP90(model.getResidualP90());
        outcome.setCoverage(HUNDRED);
        fillSnapshotDates(context, outcome);
        fillAllocationDate(context, outcome);
        fillPointOrTier(outcome, scale(model.predict(quote.getChangePercent())), stats);
        outcome.getRemarks().add(DISCLAIMER);
        outcome.getRemarks().add(model.isFallback() ? GOLD_DIRECT
                : "黄金按 β 校正估计，β=" + model.getBeta().setScale(4, RoundingMode.HALF_UP));
    }

    /**
     * 持仓加权分支，残差按 0 计，港股按汇率折算
     *
     * @param context 预估输入
     * @param outcome 输出
     */
    private void estimateByHoldings(EstimateContext context, EstimateOutcome outcome) {
        if (context.getSnapshot() == null) {
            unavailable(outcome, NO_SNAPSHOT, null);
            return;
        }
        if (context.getSnapshot().getNetWorthDate() == null) {
            unavailable(outcome, NEW_FUND, null);
            return;
        }
        List<HoldingItemVO> holdings = context.getSnapshot().getStockHoldings();
        if (holdings == null || holdings.isEmpty()) {
            unavailable(outcome, NO_HOLDING, null);
            return;
        }
        outcome.setHoldingPeriod(context.getSnapshot().getHoldingPeriod());
        outcome.setHoldingCutoffDate(context.getSnapshot().getHoldingCutoffDate());
        outcome.setHoldingDisclosureDate(context.getSnapshot().getHoldingDisclosureDate());
        outcome.setNetWorthDate(context.getSnapshot().getNetWorthDate());
        WeightedOutcome weighted = holdingWeightCalculator.weighted(holdings, context.getHoldingQuotes(),
                context.getForex());
        outcome.setCoverage(weighted.getUsedWeight());
        if (weighted.empty()) {
            unavailable(outcome, weighted.getSuspendedSymbols() != null ? SUSPENDED : QUOTE_MISSING,
                    weighted.getSuspendedSymbols());
            return;
        }
        if (weighted.getUsedWeight().compareTo(thresholds.getMinCoverage()) < 0) {
            unavailable(outcome, LOW_COVERAGE, "可用权重 " + plain(weighted.getUsedWeight()) + "%");
            return;
        }
        BigDecimal excluded = weighted.getDisclosedWeight().subtract(weighted.getUsedWeight());
        if (excluded.compareTo(thresholds.getMaxExcludedWeight()) > 0) {
            // 剔除过多时点估计已不可信，宁可不出数也不能出偏数
            unavailable(outcome, weighted.getSuspendedSymbols() != null ? SUSPENDED : HIGH_EXCLUSION,
                    weighted.getSuspendedSymbols() == null ? "剔除权重 " + plain(excluded) + "%"
                            : weighted.getSuspendedSymbols());
            return;
        }
        EstimateHistoryStats stats = stats(context);
        outcome.setTier(tierResolver.resolve(outcome.getCategory(), stats, recentStats(context),
                context.getPreviousTier(), thresholds));
        outcome.setConfidence(confidenceResolver.resolve(weighted.getUsedWeight(), stats,
                context.isDegraded(), thresholds));
        outcome.setQuoteDate(quoteDate(context.getHoldingQuotes()));
        outcome.setRealtime(anyRealtime(context.getHoldingQuotes()));
        fillPointOrTier(outcome, scale(weighted.getChange()), stats);
        fillHoldingRemarks(context, weighted, outcome);
    }

    /**
     * 从快照带出净值日期、持仓截止日与披露日，验收要求每只结果都带这些日期
     *
     * @param context 预估输入
     * @param outcome 输出
     */
    private void fillSnapshotDates(EstimateContext context, EstimateOutcome outcome) {
        if (context.getSnapshot() == null) {
            return;
        }
        outcome.setNetWorthDate(context.getSnapshot().getNetWorthDate());
        outcome.setHoldingPeriod(context.getSnapshot().getHoldingPeriod());
        outcome.setHoldingCutoffDate(context.getSnapshot().getHoldingCutoffDate());
        outcome.setHoldingDisclosureDate(context.getSnapshot().getHoldingDisclosureDate());
    }

    /**
     * 无股票持仓的基金用季末仓位报告期作为持仓截止日，保证每只结果都带报告期
     *
     * @param context 预估输入
     * @param outcome 输出
     */
    private void fillAllocationDate(EstimateContext context, EstimateOutcome outcome) {
        if (context.getSnapshot() == null || outcome.getHoldingCutoffDate() != null) {
            return;
        }
        outcome.setHoldingCutoffDate(context.getSnapshot().getAllocationReportDate());
    }

    /**
     * 按档位决定给数字、给方向与区间、还是什么都不给
     *
     * @param outcome 输出
     * @param change  内部点估计，百分点
     * @param stats   历史误差统计
     */
    private void fillPointOrTier(EstimateOutcome outcome, BigDecimal change, EstimateHistoryStats stats) {
        if (change == null) {
            unavailable(outcome, QUOTE_MISSING, null);
            return;
        }
        if (outcome.getTier() == EstimateTier.A) {
            outcome.setEstimatedChange(change);
            return;
        }
        if (outcome.getTier() == EstimateTier.C && weakDirection(stats)) {
            // C 档且方向也不可信时连方向都不给，彻底不显示
            unavailable(outcome, DIRECTIONS_WEAK, null);
            return;
        }
        // B 档与方向仍可信的 C 档都只给方向，绝不给单一数字
        outcome.setDirection(directionOf(change));
        outcome.setEstimatedChange(null);
        if (outcome.getTier() != EstimateTier.B) {
            return;
        }
        BigDecimal halfWidth = halfWidth(stats);
        outcome.setIntervalLow(scale(change.subtract(halfWidth)));
        outcome.setIntervalHigh(scale(change.add(halfWidth)));
        // 区间跨零时方向本身也不确定，必须同屏说明，避免使用者把方向当结论
        if (outcome.getIntervalLow().signum() < 0 && outcome.getIntervalHigh().signum() > 0) {
            outcome.getRemarks().add(INTERVAL_CROSS_ZERO);
        }
    }

    /**
     * 判断方向准确率是否已低于 B 档要求
     *
     * @param stats 历史误差统计
     * @return 低于阈值返回 true
     */
    private boolean weakDirection(EstimateHistoryStats stats) {
        return stats == null || stats.getDirectionAccuracy() == null
                || stats.getDirectionAccuracy().compareTo(thresholds.getBDirectionAccuracy()) < 0;
    }

    /**
     * 取区间半宽，无历史 sigma 时用默认 sigma 兜底
     *
     * @param stats 历史误差统计
     * @return 半宽，百分点
     */
    private BigDecimal halfWidth(EstimateHistoryStats stats) {
        if (stats != null && stats.getIntervalHalfWidth() != null) {
            return stats.getIntervalHalfWidth();
        }
        return thresholds.getDefaultSigma().multiply(thresholds.getSigmaFactor());
    }

    /**
     * 预估涨跌转方向，绝对值小于 0.05 个百分点按横盘
     *
     * @param change 预估涨跌幅
     * @return 方向
     */
    private ChangeDirection directionOf(BigDecimal change) {
        if (change.abs().compareTo(FLAT_THRESHOLD) < 0) {
            return ChangeDirection.FLAT;
        }
        return change.signum() > 0 ? ChangeDirection.UP : ChangeDirection.DOWN;
    }

    /**
     * 拼装持仓分支的说明文字
     *
     * @param context  预估输入
     * @param weighted 加权结果
     * @param outcome  输出，直接追加说明
     */
    private void fillHoldingRemarks(EstimateContext context, WeightedOutcome weighted, EstimateOutcome outcome) {
        outcome.getRemarks().add(DISCLAIMER);
        if (context.getVariety().getCategory() == FundCategory.MIXED) {
            outcome.getRemarks().add(MIXED_NOTE);
        }
        if (context.isHkHoldings()) {
            outcome.getRemarks().add(HK_NOTE);
        }
        if (weighted.getExcludedCount() > 0) {
            outcome.getRemarks().add("有 " + weighted.getExcludedCount() + " 只重仓股无行情或停牌，已从加权中剔除");
        }
        if (outcome.getCoverage() != null
                && outcome.getCoverage().compareTo(thresholds.getMediumCoverage()) < 0) {
            outcome.getRemarks().add(LOW_COVERAGE_NOTE);
        }
    }

    /**
     * 标记为不可预估并给出原因，同时清空所有数字
     *
     * @param outcome 输出
     * @param reason  原因
     * @param detail  细节
     */
    private void unavailable(EstimateOutcome outcome, String reason, String detail) {
        outcome.setTier(EstimateTier.C);
        outcome.setConfidence(EstimateConfidence.LOW);
        outcome.markUnavailable(reason, detail);
    }

    /**
     * 无净值历史时的退化 β 模型，等效于直取代理
     *
     * @return β 模型
     */
    private GoldBetaModel directModel() {
        GoldBetaModel model = new GoldBetaModel();
        model.setBeta(BigDecimal.ONE);
        model.setAlpha(BigDecimal.ZERO);
        model.setSamples(0);
        model.setFallback(true);
        return model;
    }

    /**
     * 取行情中的最新交易日
     *
     * @param quotes 行情集合
     * @return 最新交易日，全部不可用时为 null
     */
    private LocalDate quoteDate(Map<String, MarketQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }
        return quotes.values().stream().filter(MarketQuote::usable).map(MarketQuote::getTradeDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
    }

    /**
     * 判断是否有行情来自交易时段内
     *
     * @param quotes 行情集合
     * @return 任一实时返回 true
     */
    private Boolean anyRealtime(Map<String, MarketQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return Boolean.FALSE;
        }
        return quotes.values().stream().anyMatch(quote -> Boolean.TRUE.equals(quote.getRealtime()));
    }

    /**
     * 取长窗口误差统计
     *
     * @param context 预估输入
     * @return 统计结果
     */
    private EstimateHistoryStats stats(EstimateContext context) {
        return errorHistoryCalculator.summarize(context.getHistory(), thresholds.getHistoryWindow(),
                thresholds.getSigmaFactor());
    }

    /**
     * 取短窗口误差统计，用于与长窗口交叉校验档位
     *
     * @param context 预估输入
     * @return 统计结果
     */
    private EstimateHistoryStats recentStats(EstimateContext context) {
        return errorHistoryCalculator.summarize(context.getHistory(), thresholds.getShortWindow(),
                thresholds.getSigmaFactor());
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 统一保留四位小数
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
