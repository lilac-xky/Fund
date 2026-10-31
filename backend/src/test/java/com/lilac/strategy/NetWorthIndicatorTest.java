package com.lilac.strategy;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.strategy.model.IndicatorPlan;
import com.lilac.strategy.model.IndicatorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场外基金用净值算指标的测试，重点是「RSI 与 MACD 可算」与「KDJ 明确不可算」
 */
class NetWorthIndicatorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    /**
     * 只有净值的序列上 RSI 与 MACD 必须算得出来，这是场外基金能看到指标的前提
     */
    @Test
    @DisplayName("净值序列上 RSI 与 MACD 可算")
    void rsiAndMacdComputedFromNetWorth() {
        List<MarketBar> bars = netWorthBars(80, "1.1000", "0.0010");

        IndicatorResult result = new IndicatorCalculator().compute(bars, plan());

        assertThat(result.getRsi()).isNotNull();
        assertThat(result.getDif()).isNotNull();
        assertThat(result.getDea()).isNotNull();
        assertThat(result.getSamples()).isEqualTo(80);
    }

    /**
     * 净值没有最高最低价，KDJ 必须为 null 并在说明里点明原因，不能静默留空
     */
    @Test
    @DisplayName("净值序列上 KDJ 不可算且说明原因")
    void kdjUnavailableFromNetWorth() {
        List<MarketBar> bars = netWorthBars(80, "1.1000", "0.0010");

        IndicatorResult result = new IndicatorCalculator().compute(bars, plan());

        assertThat(result.getK()).isNull();
        assertThat(result.getD()).isNull();
        assertThat(result.getJ()).isNull();
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("最高价或最低价"));
    }

    /**
     * 净值口径下不算出全部三指标，complete 必须为 false，避免页面显示成"全部可用"
     */
    @Test
    @DisplayName("净值口径下 complete 为 false")
    void incompleteWhenKdjMissing() {
        IndicatorResult result = new IndicatorCalculator().compute(netWorthBars(80, "1.1000", "0.0010"), plan());

        assertThat(result.complete()).isFalse();
    }

    /**
     * 真实场外债基的净值波动极小，RSI 不应被算成边界值
     */
    @Test
    @DisplayName("低波动净值序列上 RSI 落在合理区间")
    void lowVolatilityNetWorthRsiReasonable() {
        // 模拟债基：每日涨跌 0.01~0.02%，交替微幅上行
        List<MarketBar> bars = new ArrayList<>();
        BigDecimal value = new BigDecimal("1.1557");
        for (int i = 0; i < 80; i++) {
            bars.add(new MarketBar(START.plusDays(i), value));
            BigDecimal step = i % 3 == 0 ? new BigDecimal("-0.0001") : new BigDecimal("0.0002");
            value = value.add(step).setScale(6, RoundingMode.HALF_UP);
        }

        IndicatorResult result = new IndicatorCalculator().compute(bars, plan());

        assertThat(result.getRsi()).isNotNull();
        assertThat(result.getRsi()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
    }

    /**
     * 净值点转日线形状时只带收盘价，与 IndicatorServiceImpl 的转换同口径
     */
    @Test
    @DisplayName("净值点转换后只含收盘价")
    void netWorthPointHasCloseOnly() {
        List<FundNetWorthPoint> points = List.of(
                new FundNetWorthPoint(START, new BigDecimal("1.10"), new BigDecimal("0.02")),
                new FundNetWorthPoint(START.plusDays(1), new BigDecimal("1.11"), new BigDecimal("0.91")));

        List<MarketBar> bars = new ArrayList<>();
        for (FundNetWorthPoint point : points) {
            bars.add(new MarketBar(point.getNetWorthDate(), point.getUnitNetWorth()));
        }

        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).getClose()).isEqualByComparingTo("1.10");
        assertThat(bars.get(0).getHigh()).isNull();
        assertThat(bars.get(0).getLow()).isNull();
        assertThat(bars.get(0).hasRange()).isFalse();
    }

    /**
     * 构造只带收盘价的净值序列
     *
     * @param count 根数
     * @param start 起始净值
     * @param step  每步变化
     * @return 序列
     */
    private List<MarketBar> netWorthBars(int count, String start, String step) {
        List<MarketBar> bars = new ArrayList<>(count);
        BigDecimal value = new BigDecimal(start);
        BigDecimal delta = new BigDecimal(step);
        for (int i = 0; i < count; i++) {
            bars.add(new MarketBar(START.plusDays(i), value));
            value = value.add(delta).setScale(6, RoundingMode.HALF_UP);
        }
        return bars;
    }

    /**
     * 构造业内通用参数
     *
     * @return 参数
     */
    private IndicatorPlan plan() {
        IndicatorPlan plan = new IndicatorPlan();
        plan.setRsiPeriod(14);
        plan.setRsiUpper(new BigDecimal("70"));
        plan.setRsiLower(new BigDecimal("30"));
        plan.setKdjPeriod(9);
        plan.setKdjKSmooth(3);
        plan.setKdjDSmooth(3);
        plan.setMacdFast(12);
        plan.setMacdSlow(26);
        plan.setMacdSignal(9);
        plan.setParticipateInAlert(Boolean.TRUE);
        return plan;
    }
}
