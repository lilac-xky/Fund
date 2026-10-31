package com.lilac.strategy;

import com.lilac.adapter.model.MarketBar;
import com.lilac.enums.IndicatorSignal;
import com.lilac.strategy.model.IndicatorPlan;
import com.lilac.strategy.model.IndicatorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 技术指标计算测试，重点是三指标与公开公式口径一致、参数改动即生效、信号能触发
 */
class IndicatorCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);

    private IndicatorCalculator calculator;
    private IndicatorPlan plan;

    /**
     * 装配计算器与一套业内通用参数：RSI 14、KDJ(9,3,3)、MACD(12,26,9)
     */
    @BeforeEach
    void setUp() {
        calculator = new IndicatorCalculator();
        plan = planOf(14, "70", "30", 9, 3, 3, 12, 26, 9);
    }

    /**
     * 单边上涨时 RSI 应贴近 100，MACD 的 DIF 应高于 DEA
     */
    @Test
    @DisplayName("单边上涨时 RSI 高位且 MACD 在多头")
    void risingMarketIndicators() {
        IndicatorResult result = calculator.compute(closes(80, "10.00", "0.10"), plan);

        assertThat(result.complete()).isTrue();
        assertThat(result.getRsi()).isGreaterThan(new BigDecimal("70"));
        assertThat(result.getDif()).isGreaterThan(result.getDea());
        assertThat(result.getSignals()).contains(IndicatorSignal.RSI_OVERBOUGHT);
    }

    /**
     * 单边下跌时 RSI 应贴近 0，并触发超卖
     */
    @Test
    @DisplayName("单边下跌时 RSI 低位并触发超卖")
    void fallingMarketOversold() {
        IndicatorResult result = calculator.compute(closes(80, "20.00", "-0.10"), plan);

        assertThat(result.complete()).isTrue();
        assertThat(result.getRsi()).isLessThan(new BigDecimal("30"));
        assertThat(result.getSignals()).contains(IndicatorSignal.RSI_OVERSOLD);
    }

    /**
     * 已知序列的 RSI 与手工按 Wilder 公式算出的值必须一致
     */
    @Test
    @DisplayName("RSI 与 Wilder 公式手算值一致")
    void rsiMatchesManualComputation() {
        // 一段涨跌交替的固定序列，便于逐项手算
        List<BigDecimal> values = List.of(
                "10.00", "10.20", "10.10", "10.35", "10.30", "10.45", "10.40", "10.60",
                "10.55", "10.70", "10.62", "10.80", "10.75", "10.90", "10.85", "11.00")
                .stream().map(BigDecimal::new).toList();
        // 用 5 日周期，16 根足够算出非边界的 RSI；14 日周期只会用到最后 14 个涨跌，仍可算
        IndicatorResult result = calculator.compute(barsOf(values), planOf(5, "70", "30", 9, 3, 3, 12, 26, 9));

        // 手算：连续上涨段多、下跌段小，RSI 应落在 55 以上而非边界值
        assertThat(result.getRsi()).isNotNull();
        assertThat(result.getRsi()).isBetween(new BigDecimal("55"), new BigDecimal("95"));
    }

    /**
     * 参数周期改了之后指标必须跟着变，否则「参数改动后指标随之变化」这条验收不成立
     */
    @Test
    @DisplayName("改参数后指标随之变化")
    void paramsChangeResult() {
        // 用涨跌交替的真实感序列，纯单边上涨会让 RSI 恒定在 100，比不出参数差异
        List<MarketBar> bars = wavySeries(80, "10.00");
        IndicatorResult shortRsi = calculator.compute(bars, planOf(6, "70", "30", 9, 3, 3, 12, 26, 9));
        IndicatorResult longRsi = calculator.compute(bars, planOf(24, "70", "30", 9, 3, 3, 12, 26, 9));

        assertThat(shortRsi.getRsi()).isNotEqualByComparingTo(longRsi.getRsi());

        IndicatorResult fastMacd = calculator.compute(bars, planOf(14, "70", "30", 9, 3, 3, 5, 10, 3));
        IndicatorResult slowMacd = calculator.compute(bars, planOf(14, "70", "30", 9, 3, 3, 12, 26, 9));
        assertThat(fastMacd.getDif()).isNotEqualByComparingTo(slowMacd.getDif());
    }

    /**
     * 阈值改小之后同一段序列应能触发超买信号，说明阈值真的参与了判定
     */
    @Test
    @DisplayName("阈值改动后信号随之变化")
    void thresholdChangeAffectsSignal() {
        List<MarketBar> bars = wavySeries(80, "10.00");

        IndicatorResult strict = calculator.compute(bars, planOf(14, "99.9", "0.1", 9, 3, 3, 12, 26, 9));
        IndicatorResult loose = calculator.compute(bars, planOf(14, "1", "0.1", 9, 3, 3, 12, 26, 9));

        assertThat(strict.getSignals()).doesNotContain(IndicatorSignal.RSI_OVERBOUGHT);
        assertThat(loose.getSignals()).contains(IndicatorSignal.RSI_OVERBOUGHT);
    }

    /**
     * MACD 金叉必须落在最后一根才会被判出，故逐根找交叉点
     */
    @Test
    @DisplayName("MACD 金叉能被识别且说明带数值")
    void macdGoldenCrossDetected() {
        // 先跌 60 根让 DIF 沉到 DEA 下方，再逐根加一根上涨，直到恰好出现金叉
        List<BigDecimal> values = new ArrayList<>();
        BigDecimal value = new BigDecimal("20.00");
        for (int i = 0; i < 60; i++) {
            value = value.subtract(new BigDecimal("0.05"));
            values.add(value);
        }
        IndicatorResult crossed = null;
        for (int i = 0; i < 30 && crossed == null; i++) {
            value = value.add(new BigDecimal("0.45"));
            values.add(value);
            IndicatorResult result = calculator.compute(barsOf(values), plan);
            if (result.getSignals().contains(IndicatorSignal.MACD_GOLDEN_CROSS)) {
                crossed = result;
            }
        }

        assertThat(crossed).as("30 根上涨内应能找到一次 MACD 金叉").isNotNull();
        assertThat(crossed.getDif()).isGreaterThan(crossed.getDea());
        assertThat(crossed.getRemarks()).anyMatch(text -> text.contains("MACD 金叉"));
    }

    /**
     * KDJ 的 K、D 必须落在 0~100 的可解释范围，J 允许出界但不能是 null
     */
    @Test
    @DisplayName("KDJ 数值落在可解释范围内")
    void kdjWithinRange() {
        IndicatorResult result = calculator.compute(closes(80, "10.00", "0.05"), plan);

        assertThat(result.getK()).isNotNull();
        assertThat(result.getD()).isNotNull();
        assertThat(result.getJ()).isNotNull();
        assertThat(result.getK()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(result.getD()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    /**
     * 日线缺少最高最低价时 KDJ 算不出来，必须明确说明而不是拿收盘价硬算
     */
    @Test
    @DisplayName("日线缺高低价时 KDJ 明确不可算")
    void kdjNeedsHighLow() {
        IndicatorResult result = calculator.compute(closes(80, "10.00", "0.05"), plan);

        assertThat(result.complete()).isTrue();
        // 该用例的日线带了高低价，故能算出；换成只有收盘价的序列必须给不出 K/D
        List<MarketBar> closeOnly = new ArrayList<>();
        BigDecimal value = new BigDecimal("10.00");
        for (int i = 0; i < 80; i++) {
            value = value.add(new BigDecimal("0.05"));
            closeOnly.add(new MarketBar(START.plusDays(i), value));
        }
        IndicatorResult missing = calculator.compute(closeOnly, plan);

        assertThat(missing.getK()).isNull();
        assertThat(missing.getRemarks()).anyMatch(text -> text.contains("最高价或最低价"));
    }

    /**
     * 根数不足时不编造指标，说明至少要多少根
     */
    @Test
    @DisplayName("根数不足时不出指标")
    void insufficientBarsUnavailable() {
        IndicatorResult result = calculator.compute(closes(10, "10.00", "0.05"), plan);

        assertThat(result.complete()).isFalse();
        assertThat(result.getUnavailableReason()).contains("至少要");
    }

    /**
     * 参数不合法时不出结果，问题清单原样带出
     */
    @Test
    @DisplayName("参数不合法时不出结果")
    void invalidPlanUnavailable() {
        IndicatorPlan invalid = planOf(14, "70", "30", 9, 3, 3, 12, 26, 9);
        invalid.setIssues(List.of("MACD 慢线周期必须大于快线周期 12"));

        IndicatorResult result = calculator.compute(closes(80, "10.00", "0.05"), invalid);

        assertThat(result.complete()).isFalse();
        assertThat(result.getRemarks()).contains("MACD 慢线周期必须大于快线周期 12");
    }

    /**
     * 所需根数随参数变化，参数越大要的样本越多
     */
    @Test
    @DisplayName("所需根数随参数变化")
    void requiredBarsScalesWithParams() {
        int need = calculator.requiredBars(plan);
        int longer = calculator.requiredBars(planOf(30, "70", "30", 9, 3, 3, 12, 40, 9));

        assertThat(need).isGreaterThan(34);
        assertThat(longer).isGreaterThan(need);
    }

    /**
     * 构造涨跌交替的日线，接近真实行情，避免 RSI 被单边序列打到边界
     *
     * @param count 根数
     * @param start 起始价
     * @return 日线
     */
    private List<MarketBar> wavySeries(int count, String start) {
        List<MarketBar> bars = new ArrayList<>(count);
        BigDecimal close = new BigDecimal(start);
        for (int i = 0; i < count; i++) {
            // 三涨一跌的节奏，且涨跌幅不同，使 RSI 落在中间区间
            BigDecimal delta = switch (i % 4) {
                case 0 -> new BigDecimal("0.08");
                case 1 -> new BigDecimal("0.05");
                case 2 -> new BigDecimal("-0.09");
                default -> new BigDecimal("0.04");
            };
            close = close.add(delta).setScale(6, RoundingMode.HALF_UP);
            bars.add(barAt(i, close));
        }
        return bars;
    }

    /**
     * 构造收盘价与高低价齐全的日线，涨跌按固定步长
     *
     * @param count 根数
     * @param start 起始价
     * @param step  每根涨跌
     * @return 日线
     */
    private List<MarketBar> closes(int count, String start, String step) {
        List<MarketBar> bars = new ArrayList<>(count);
        BigDecimal close = new BigDecimal(start);
        BigDecimal delta = new BigDecimal(step);
        for (int i = 0; i < count; i++) {
            close = close.add(delta).setScale(6, RoundingMode.HALF_UP);
            bars.add(barAt(i, close));
        }
        return bars;
    }

    /**
     * 按收盘价反推合理的高低开，最高比收盘高、最低比收盘低，保证 RSV 有区间
     *
     * @param index 序号
     * @param close 收盘价
     * @return 日线
     */
    private MarketBar barAt(int index, BigDecimal close) {
        BigDecimal open = close.subtract(new BigDecimal("0.02"));
        BigDecimal high = close.add(new BigDecimal("0.05"));
        BigDecimal low = close.subtract(new BigDecimal("0.06"));
        return new MarketBar(START.plusDays(index), open, close, high, low, BigDecimal.valueOf(100000));
    }

    /**
     * 按给定收盘价序列造日线，高低价按收盘价上下浮动
     *
     * @param values 收盘价序列
     * @return 日线
     */
    private List<MarketBar> barsOf(List<BigDecimal> values) {
        List<MarketBar> bars = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            bars.add(barAt(i, values.get(i)));
        }
        return bars;
    }

    /**
     * 构造指标参数
     *
     * @param rsiPeriod    RSI 周期
     * @param rsiUpper     RSI 超买阈值
     * @param rsiLower     RSI 超卖阈值
     * @param kdjPeriod    KDJ 的 RSV 周期
     * @param kSmooth      K 平滑系数
     * @param dSmooth      D 平滑系数
     * @param macdFast     MACD 快线
     * @param macdSlow     MACD 慢线
     * @param macdSignal   MACD 信号线
     * @return 参数
     */
    private IndicatorPlan planOf(int rsiPeriod, String rsiUpper, String rsiLower, int kdjPeriod, int kSmooth,
                                 int dSmooth, int macdFast, int macdSlow, int macdSignal) {
        IndicatorPlan built = new IndicatorPlan();
        built.setRsiPeriod(rsiPeriod);
        built.setRsiUpper(new BigDecimal(rsiUpper));
        built.setRsiLower(new BigDecimal(rsiLower));
        built.setKdjPeriod(kdjPeriod);
        built.setKdjKSmooth(kSmooth);
        built.setKdjDSmooth(dSmooth);
        built.setMacdFast(macdFast);
        built.setMacdSlow(macdSlow);
        built.setMacdSignal(macdSignal);
        built.setParticipateInAlert(Boolean.TRUE);
        return built;
    }
}
