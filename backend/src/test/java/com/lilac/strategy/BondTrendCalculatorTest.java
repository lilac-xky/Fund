package com.lilac.strategy;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.enums.BondTrendSignal;
import com.lilac.strategy.model.BondPlan;
import com.lilac.strategy.model.BondTrendResult;
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
 * 债券趋势计算测试，核心是「横盘能算准」「转弱能识别」「未达标不提醒」「数据不足不出结论」
 */
class BondTrendCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    private BondTrendCalculator calculator;
    private BondPlan plan;

    /**
     * 装配计算器与一组基准参数：窗口 60、横盘 20 日、带宽 0.02、涨幅上限 0.10
     */
    @BeforeEach
    void setUp() {
        calculator = new BondTrendCalculator();
        plan = planOf(60, "0.02", 20, "0.10", "0.50", "0.30");
    }

    /**
     * 净值长期不动时应算出横盘天数并触发换仓提醒
     */
    @Test
    @DisplayName("净值长期不动算出横盘并触发提醒")
    void detectsSideways() {
        List<FundNetWorthPoint> points = flatSeries(40, "1.0500");

        BondTrendResult result = calculator.evaluate(points, plan);

        assertThat(result.getSignal()).isEqualTo(BondTrendSignal.SIDEWAYS);
        assertThat(result.getSignal().alerting()).isTrue();
        assertThat(result.getSidewaysDays()).isGreaterThanOrEqualTo(20);
        assertThat(result.getSidewaysReturn()).isNotNull();
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("连续横盘"));
    }

    /**
     * 净值稳定上涨时既不横盘也不转弱，必须给正常而不是提醒
     */
    @Test
    @DisplayName("稳定上涨时判为正常且不提醒")
    void risingIsNormal() {
        // 每日稳涨 0.05%，超出横盘带宽 0.02%，故不判横盘；斜率向上故不判转弱
        List<FundNetWorthPoint> points = linearSeries(40, "1.0000", "0.0005");

        BondTrendResult result = calculator.evaluate(points, plan);

        assertThat(result.getSignal()).isEqualTo(BondTrendSignal.NORMAL);
        assertThat(result.getSignal().alerting()).isFalse();
        assertThat(result.getAnnualizedSlope()).isPositive();
    }

    /**
     * 净值持续下跌且波动够大时判为趋势转弱，这是「该撤」的信号
     */
    @Test
    @DisplayName("持续下跌且波动放大时判为趋势转弱")
    void decliningIsWeak() {
        List<FundNetWorthPoint> points = zigzagDecline(40, "1.0500");

        BondTrendResult result = calculator.evaluate(points, plan);

        assertThat(result.getSignal()).isEqualTo(BondTrendSignal.WEAK);
        assertThat(result.getSignal().alerting()).isTrue();
        assertThat(result.getAnnualizedSlope()).isNegative();
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("趋势已转弱"));
    }

    /**
     * 横盘天数够但累计涨幅超过上限时不算横盘，避免把慢涨判成不动
     */
    @Test
    @DisplayName("累计涨幅超过上限时不算横盘")
    void sidewaysNeedsLowReturn() {
        // 上限压到 −1%，横盘区间几乎没有涨幅故必然高于它，不该判横盘
        plan.setSidewaysReturn(new BigDecimal("-1"));
        List<FundNetWorthPoint> points = flatSeries(40, "1.0500");

        BondTrendResult result = calculator.evaluate(points, plan);

        assertThat(result.getSignal()).isNotEqualTo(BondTrendSignal.SIDEWAYS);
    }

    /**
     * 净值点数不足时不编造结论，明确说明缺多少
     */
    @Test
    @DisplayName("数据不足时不出结论")
    void insufficientDataUnavailable() {
        List<FundNetWorthPoint> points = flatSeries(3, "1.0500");

        BondTrendResult result = calculator.evaluate(points, plan);

        assertThat(result.getSignal()).isEqualTo(BondTrendSignal.UNAVAILABLE);
        assertThat(result.getUnavailableReason()).contains("至少要");
        assertThat(result.getSidewaysDays()).isNull();
    }

    /**
     * 参数不合法时不出结论，并把问题清单带出来
     */
    @Test
    @DisplayName("参数不合法时不出结论")
    void invalidPlanUnavailable() {
        BondPlan invalid = planOf(60, "0.02", 20, "0.10", "0.50", "0.30");
        invalid.setIssues(List.of("横盘天数 20 不得超过回看窗口 10 个交易日"));

        BondTrendResult result = calculator.evaluate(flatSeries(40, "1.0500"), invalid);

        assertThat(result.getSignal()).isEqualTo(BondTrendSignal.UNAVAILABLE);
        assertThat(result.getRemarks()).contains("横盘天数 20 不得超过回看窗口 10 个交易日");
    }

    /**
     * 触发时的说明必须带具体数值，不能只给一句「考虑换仓」
     */
    @Test
    @DisplayName("触发说明里给出触发依据的数值")
    void remarksCarryNumbers() {
        BondTrendResult result = calculator.evaluate(flatSeries(40, "1.0500"), plan);

        assertThat(result.getRemarks()).anyMatch(text -> text.contains("累计涨幅"));
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("年化斜率"));
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("日波动率"));
        assertThat(result.getWindowStart()).isNotNull();
        assertThat(result.getWindowEnd()).isNotNull();
        assertThat(result.getSamples()).isGreaterThan(0);
    }

    /**
     * 未触发时说明差在哪，使用者才知道离阈值多远
     */
    @Test
    @DisplayName("未触发时说明与阈值的差距")
    void normalExplainsGap() {
        BondTrendResult result = calculator.evaluate(linearSeries(40, "1.0000", "0.0005"), plan);

        assertThat(result.getRemarks()).anyMatch(text -> text.contains("未达换仓提醒条件"));
        assertThat(result.getRemarks()).anyMatch(text -> text.contains("阈值"));
    }

    /**
     * 自然日跨度按日期差算，供文案给出「横盘多少天」
     */
    @Test
    @DisplayName("能算出横盘的自然日跨度")
    void calendarDaysComputed() {
        assertThat(calculator.naturalDaysBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)))
                .isEqualTo(31L);
        assertThat(calculator.naturalDaysBetween(null, LocalDate.of(2026, 8, 1))).isZero();
    }

    /**
     * 构造横盘序列，净值在极窄区间内来回微动
     *
     * @param count 点数
     * @param base  中心净值
     * @return 净值序列
     */
    private List<FundNetWorthPoint> flatSeries(int count, String base) {
        BigDecimal center = new BigDecimal(base);
        List<FundNetWorthPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 上下各 0.005%，远小于 0.02% 的横盘带宽，整体不累积上涨
            BigDecimal offset = i % 2 == 0 ? new BigDecimal("0.00005") : new BigDecimal("-0.00005");
            points.add(new FundNetWorthPoint(START.plusDays(i), center.add(offset), null));
        }
        return points;
    }

    /**
     * 按固定涨幅线性生成净值，用于构造稳定的上涨与下跌
     *
     * @param count 点数
     * @param start 起始净值
     * @param step  每步涨幅
     * @return 净值序列
     */
    private List<FundNetWorthPoint> linearSeries(int count, String start, String step) {
        List<FundNetWorthPoint> points = new ArrayList<>(count);
        BigDecimal value = new BigDecimal(start);
        BigDecimal delta = new BigDecimal(step);
        for (int i = 0; i < count; i++) {
            points.add(new FundNetWorthPoint(START.plusDays(i), value, null));
            value = value.add(delta);
        }
        return points;
    }

    /**
     * 构造锯齿下跌序列：整体下行且每日波动足够大，用于判定趋势转弱
     *
     * @param count 点数
     * @param start 起始净值
     * @return 净值序列
     */
    private List<FundNetWorthPoint> zigzagDecline(int count, String start) {
        List<FundNetWorthPoint> points = new ArrayList<>(count);
        BigDecimal value = new BigDecimal(start);
        for (int i = 0; i < count; i++) {
            // 主跌日 −0.6%、反弹日 +0.4%，净效果持续向下，日波动超过转弱波动率 0.30%
            BigDecimal step = i % 2 == 0 ? new BigDecimal("-0.006") : new BigDecimal("0.004");
            value = value.add(step).setScale(6, RoundingMode.HALF_UP);
            points.add(new FundNetWorthPoint(START.plusDays(i), value, null));
        }
        return points;
    }

    /**
     * 构造监控参数
     *
     * @param window         回看窗口
     * @param band           横盘带宽
     * @param days           横盘天数
     * @param sidewaysReturn 横盘涨幅上限
     * @param weakSlope      转弱斜率
     * @param weakVolatility 转弱波动率
     * @return 参数
     */
    private BondPlan planOf(int window, String band, int days, String sidewaysReturn, String weakSlope,
                            String weakVolatility) {
        BondPlan built = new BondPlan();
        built.setWindow(window);
        built.setSidewaysBand(new BigDecimal(band));
        built.setSidewaysDays(days);
        built.setSidewaysReturn(new BigDecimal(sidewaysReturn));
        built.setWeakSlope(new BigDecimal(weakSlope));
        built.setWeakVolatility(new BigDecimal(weakVolatility));
        return built;
    }
}
