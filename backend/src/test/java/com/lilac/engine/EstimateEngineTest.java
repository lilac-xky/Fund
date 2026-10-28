package com.lilac.engine;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.model.EstimateContext;
import com.lilac.engine.model.EstimateOutcome;
import com.lilac.engine.model.GoldBetaModel;
import com.lilac.enums.ChangeDirection;
import com.lilac.enums.EstimateBranch;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预估引擎测试，覆盖四条分支、档位输出与全部「无法预估」分支
 */
class EstimateEngineTest {

    private EstimateEngine engine;
    private EstimateThresholds thresholds;

    /**
     * 装配引擎，阈值取默认配置
     */
    @BeforeEach
    void setUp() {
        thresholds = new EstimateThresholds();
        engine = new EstimateEngine(new HoldingWeightCalculator(), new TierResolver(),
                new ConfidenceResolver(), new ErrorHistoryCalculator(), thresholds);
    }

    /**
     * 场内 ETF 走指数直取，结果等于对标 ETF 自身的当日涨跌，不走重仓股
     */
    @Test
    @DisplayName("场内 ETF 走指数直取")
    void etfUsesIndexOnly() {
        EstimateContext context = contextOf(variety("ETF:510300", VarietyType.ETF, "沪深300ETF", FundCategory.ETF));
        context.setRawFundType("指数型-股票");
        context.setProxySymbol("sh510300");
        context.setProxyQuote(okQuote("sh510300", "0.85"));
        context.setSnapshot(snapshotOf("0.10"));

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.INDEX_ONLY);
        assertThat(outcome.getTier()).isEqualTo(EstimateTier.A);
        assertThat(outcome.getEstimatedChange()).isEqualByComparingTo("0.8500");
        assertThat(outcome.estimable()).isTrue();
    }

    /**
     * 被动指数型场外基金走指数直取，与重仓股无关
     */
    @Test
    @DisplayName("被动指数型场外基金走指数直取")
    void indexFundUsesIndexOnly() {
        EstimateContext context = contextOf(variety("OF:110020", VarietyType.OF, "易方达沪深300ETF联接A",
                FundCategory.STOCK));
        context.setRawFundType("指数型-股票");
        context.setProxySymbol("sh000300");
        context.setProxyQuote(okQuote("sh000300", "-1.20"));
        context.setSnapshot(snapshotOf("0.10"));

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.INDEX_ONLY);
        assertThat(outcome.getEstimatedChange()).isEqualByComparingTo("-1.2000");
        assertThat(outcome.getDirection()).isNull();
    }

    /**
     * 股票型走持仓加权，残差按 0 计，结果与手工核算一致
     */
    @Test
    @DisplayName("股票型走持仓加权且残差按 0 计")
    void stockUsesWeighted() {
        EstimateContext context = contextOf(variety("OF:110022", VarietyType.OF, "易方达消费行业股票",
                FundCategory.STOCK));
        context.setRawFundType("股票型");
        context.setSnapshot(snapshotOf("94.27"));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "2.00"));
        quotes.put("sz000858", okQuote("sz000858", "-1.00"));
        context.setHoldingQuotes(quotes);

        EstimateOutcome outcome = engine.estimate(context);

        // 47.14% × 2.00 + 47.13% × (−1.00) = 0.4715 个百分点
        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.STOCK_WEIGHTED);
        assertThat(outcome.getEstimatedChange()).isEqualByComparingTo("0.4715");
        assertThat(outcome.getCoverage()).isEqualByComparingTo("94.27");
    }

    /**
     * 红利型即使是指数型也走持仓加权，这是 ISSUE-001 的实测取舍
     */
    @Test
    @DisplayName("红利型走持仓加权而非指数直取")
    void dividendUsesWeighted() {
        EstimateContext context = contextOf(variety("OF:090010", VarietyType.OF, "大成中证红利指数A",
                FundCategory.DIVIDEND));
        context.setRawFundType("指数型-股票");
        context.setProxySymbol("sh000922");
        context.setProxyQuote(okQuote("sh000922", "3.00"));
        context.setSnapshot(snapshotOf("94.33"));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "0.50"));
        quotes.put("sz000858", okQuote("sz000858", "1.50"));
        context.setHoldingQuotes(quotes);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.STOCK_WEIGHTED);
        assertThat(outcome.getEstimatedChange()).isNotEqualByComparingTo("3.0000");
    }

    /**
     * 黄金走 β + AR(1)，β 与 ρ 一路带到结果里
     */
    @Test
    @DisplayName("黄金走 β 加 AR(1) 分支")
    void goldUsesBetaAr() {
        EstimateContext context = contextOf(variety("OF:000216", VarietyType.OF, "华安黄金ETF联接A",
                FundCategory.GOLD));
        context.setProxySymbol("sh518880");
        context.setProxyQuote(okQuote("sh518880", "1.00"));
        GoldBetaModel model = new GoldBetaModel();
        model.setBeta(new BigDecimal("0.9175"));
        model.setAlpha(BigDecimal.ZERO);
        model.setRho(new BigDecimal("-0.20"));
        model.setLastResidual(new BigDecimal("0.10"));
        model.setSamples(30);
        model.setFallback(false);
        context.setGoldModel(model);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.GOLD_PROXY);
        assertThat(outcome.getBeta()).isEqualByComparingTo("0.9175");
        assertThat(outcome.getRho()).isEqualByComparingTo("-0.20");
        // 0.9175 × 1.00 + (−0.20) × 0.10 = 0.8975
        assertThat(outcome.getEstimatedChange()).isEqualByComparingTo("0.8975");
    }

    /**
     * 债券走债券 ETF 代理，不碰重仓股
     */
    @Test
    @DisplayName("债券走代理分支")
    void bondUsesProxy() {
        EstimateContext context = contextOf(variety("OF:217022", VarietyType.OF, "招商产业债券A",
                FundCategory.BOND));
        context.setProxySymbol("sh511010");
        context.setProxyQuote(okQuote("sh511010", "0.02"));

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.BOND_PROXY);
        assertThat(outcome.getEstimatedChange()).isEqualByComparingTo("0.0200");
        assertThat(outcome.getRemarks()).anyMatch(note -> note.contains("债券"));
    }

    /**
     * 混合型按 B 档只给方向与区间，绝不给单一数字
     */
    @Test
    @DisplayName("混合型 B 档只给方向与区间")
    void mixedTierBGivesOnlyDirection() {
        EstimateContext context = contextOf(variety("OF:005827", VarietyType.OF, "易方达蓝筹精选混合",
                FundCategory.MIXED));
        context.setRawFundType("混合型-偏股");
        context.setSnapshot(snapshotOf("76.43"));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "1.60"));
        quotes.put("sz000858", okQuote("sz000858", "1.00"));
        context.setHoldingQuotes(quotes);
        context.setHistory(mixedHistory());

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getTier()).isEqualTo(EstimateTier.B);
        assertThat(outcome.getEstimatedChange()).isNull();
        assertThat(outcome.getDirection()).isEqualTo(ChangeDirection.UP);
        assertThat(outcome.getIntervalLow()).isNotNull();
        assertThat(outcome.getIntervalHigh()).isNotNull();
        assertThat(outcome.getIntervalLow()).isLessThan(outcome.getIntervalHigh());
        assertThat(outcome.getRemarks()).anyMatch(note -> note.contains("混合型"));
    }

    /**
     * 行情取不到时整只标为不可预估，且不存在任何数字与方向
     */
    @Test
    @DisplayName("行情取不到时不输出任何数字")
    void noQuoteGivesNoNumbers() {
        EstimateContext context = contextOf(variety("OF:161725", VarietyType.OF, "招商中证白酒指数A",
                FundCategory.STOCK));
        context.setSnapshot(snapshotOf("94.27"));
        context.setHoldingQuotes(Map.of());

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getEstimatedChange()).isNull();
        assertThat(outcome.getDirection()).isNull();
        assertThat(outcome.getIntervalLow()).isNull();
        assertThat(outcome.getUnavailableReason()).contains("无法预估");
    }

    /**
     * 停牌重仓股占多数时整只进入不可预估，原因带出停牌标的
     */
    @Test
    @DisplayName("停牌重仓股导致不可预估且原因可读")
    void suspendedHoldingGivesReason() {
        EstimateContext context = contextOf(variety("OF:161725", VarietyType.OF, "招商中证白酒指数A",
                FundCategory.STOCK));
        context.setSnapshot(snapshotOf("94.27"));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        MarketQuote suspended = okQuote("sh600519", "0.00");
        suspended.setStatus(QuoteStatus.SUSPENDED);
        suspended.setChangePercent(null);
        quotes.put("sh600519", suspended);
        MarketQuote noData = MarketQuote.unavailable(MarketSymbol.of("sz000858"), QuoteStatus.NO_DATA, "无数据");
        quotes.put("sz000858", noData);
        context.setHoldingQuotes(quotes);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getUnavailableReason()).contains("停牌");
        assertThat(outcome.getUnavailableDetail()).contains("600519");
        assertThat(outcome.getEstimatedChange()).isNull();
    }

    /**
     * 新基金无净值时明确说明，不用空净值算出数字
     */
    @Test
    @DisplayName("新基金无净值时不可预估")
    void newFundNoNetWorth() {
        EstimateContext context = contextOf(variety("OF:990001", VarietyType.OF, "新发基金", FundCategory.STOCK));
        context.setSnapshot(snapshotOf("90.00"));
        context.getSnapshot().setNetWorthDate(null);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getUnavailableReason()).contains("新基金");
    }

    /**
     * 无持仓的基金不可预估，原因点明缺股票持仓
     */
    @Test
    @DisplayName("无持仓时不可预估")
    void noHoldingUnavailable() {
        EstimateContext context = contextOf(variety("OF:990002", VarietyType.OF, "纯债转股票", FundCategory.STOCK));
        FundSnapshotVO snapshot = snapshotOf("0");
        snapshot.setStockHoldings(List.of());
        context.setSnapshot(snapshot);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getUnavailableReason()).contains("股票持仓");
    }

    /**
     * 类型待确认时不出数，要求先确认类型再预估
     */
    @Test
    @DisplayName("类型待确认时不可预估")
    void unknownCategoryUnavailable() {
        EstimateContext context = contextOf(variety("OF:990003", VarietyType.OF, "待确认基金", FundCategory.UNKNOWN));

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getUnavailableReason()).contains("待确认");
        assertThat(outcome.getBranch()).isEqualTo(EstimateBranch.UNAVAILABLE);
    }

    /**
     * 覆盖率过低时不输出数字，避免用少量重仓股代表整只基金
     */
    @Test
    @DisplayName("覆盖率过低时不可预估")
    void lowCoverageUnavailable() {
        EstimateContext context = contextOf(variety("OF:990004", VarietyType.OF, "低覆盖基金", FundCategory.STOCK));
        FundSnapshotVO snapshot = snapshotOf("4.00");
        context.setSnapshot(snapshot);
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "1.00"));
        quotes.put("sz000858", okQuote("sz000858", "1.00"));
        context.setHoldingQuotes(quotes);

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.estimable()).isFalse();
        assertThat(outcome.getUnavailableReason()).contains("覆盖率");
    }

    /**
     * 有历史误差且 MAE 达标时给 A 档数字
     */
    @Test
    @DisplayName("有历史且 MAE 达标时给 A 档数字")
    void tierAWithHistory() {
        EstimateContext context = contextOf(variety("OF:161725", VarietyType.OF, "招商中证白酒指数A",
                FundCategory.STOCK));
        context.setSnapshot(snapshotOf("94.27"));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "1.00"));
        quotes.put("sz000858", okQuote("sz000858", "1.00"));
        context.setHoldingQuotes(quotes);
        context.setHistory(goodHistory());

        EstimateOutcome outcome = engine.estimate(context);

        assertThat(outcome.getTier()).isEqualTo(EstimateTier.A);
        assertThat(outcome.getEstimatedChange()).isNotNull();
    }

    /**
     * 拼装上下文
     *
     * @param variety 品种
     * @return 上下文
     */
    private EstimateContext contextOf(VarietyVO variety) {
        EstimateContext context = new EstimateContext();
        context.setVariety(variety);
        return context;
    }

    /**
     * 构造品种
     *
     * @param varietyKey 业务主键
     * @param type       品种类型
     * @param name       名称
     * @param category   基金类型
     * @return 品种
     */
    private VarietyVO variety(String varietyKey, VarietyType type, String name, FundCategory category) {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey(varietyKey);
        variety.setVarietyType(type);
        variety.setVarietyCode(varietyKey.substring(varietyKey.indexOf(':') + 1));
        variety.setVarietyName(name);
        variety.setCategory(category);
        return variety;
    }

    /**
     * 构造持仓快照，两只股票各占合计权重的一半
     *
     * @param totalWeight 合计权重
     * @return 快照
     */
    private FundSnapshotVO snapshotOf(String totalWeight) {
        BigDecimal total = new BigDecimal(totalWeight);
        BigDecimal half = total.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
        FundSnapshotVO snapshot = new FundSnapshotVO();
        snapshot.setNetWorthDate(LocalDate.of(2026, 10, 23));
        snapshot.setHoldingPeriod("2026Q2");
        snapshot.setHoldingCutoffDate(LocalDate.of(2026, 6, 30));
        snapshot.setHoldingDisclosureDate(LocalDate.of(2026, 7, 21));
        snapshot.setStockHoldings(List.of(
                new HoldingItemVO("600519", "贵州茅台", half),
                new HoldingItemVO("000858", "五粮液", total.subtract(half))));
        snapshot.setDegraded(Boolean.FALSE);
        return snapshot;
    }

    /**
     * 构造一条可用行情
     *
     * @param symbol  带前缀代码
     * @param percent 涨跌幅
     * @return 行情
     */
    private MarketQuote okQuote(String symbol, String percent) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(MarketSymbol.of(symbol));
        quote.setChangePercent(new BigDecimal(percent));
        quote.setPrice(new BigDecimal("10.00"));
        quote.setPreviousClose(new BigDecimal("9.90"));
        quote.setTradeDate(LocalDate.of(2026, 10, 28));
        quote.setStatus(QuoteStatus.OK);
        quote.setSource("stub");
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.TRUE);
        return quote;
    }

    /**
     * 造一段混合型历史：MAE 约 0.45、方向全部命中、区间宽比小于 1
     *
     * @return 历史序列
     */
    private List<EstimateErrorVO> mixedHistory() {
        List<EstimateErrorVO> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double actual = i % 2 == 0 ? 2.40 : -2.40;
            double estimated = actual + (i % 2 == 0 ? 0.45 : -0.45);
            history.add(errorRow(estimated, actual));
        }
        return history;
    }

    /**
     * 造一段 MAE 很小的历史，判定应给 A 档
     *
     * @return 历史序列
     */
    private List<EstimateErrorVO> goodHistory() {
        List<EstimateErrorVO> history = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double actual = i % 2 == 0 ? 1.20 : -1.20;
            double estimated = actual + (i % 2 == 0 ? 0.10 : -0.10);
            history.add(errorRow(estimated, actual));
        }
        return history;
    }

    /**
     * 构造一条误差记录
     *
     * @param estimated 预估涨跌幅
     * @param actual    实际涨跌幅
     * @return 误差记录
     */
    private EstimateErrorVO errorRow(double estimated, double actual) {
        EstimateErrorVO vo = new EstimateErrorVO();
        vo.setTradeDate(LocalDate.of(2026, 9, 1));
        vo.setEstimatedChange(BigDecimal.valueOf(estimated));
        vo.setActualChange(BigDecimal.valueOf(actual));
        vo.setErrorValue(BigDecimal.valueOf(estimated - actual));
        return vo;
    }
}
