package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.enums.DividendMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 现金分红推算测试，覆盖按回报识别除息、逐笔明细、红利再投与噪声过滤
 */
class DividendCalculatorTest {

    private final DividendCalculator calculator = new DividendCalculator();

    /**
     * 当日回报按除息口径给出，用它正推的单位净值与实际值之差就是每份分红
     */
    @Test
    @DisplayName("按当日回报识别除息并逐笔列出")
    void detectsDistributionFromEquityReturn() {
        LocalDate since = LocalDate.of(2026, 7, 31);
        List<FundNetWorthPoint> history = List.of(
                point("2026-07-31", "1.6208", "1.9508", "-0.3700"),
                point("2026-08-05", "1.5719", "1.9019", "-1.1300"),
                // 前一日 1.5719、回报 +0.1200% 应到 1.5738，实际 1.5688，差 0.0050 是分红
                point("2026-08-06", "1.5688", "1.9038", "0.1200"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("60.4"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        assertThat(result.distributions()).hasSize(1);
        assertThat(result.distributions().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 6));
        // 两口径差在 8-06 从 0.3350 变成 0.3350，实际由累计净值差算出 0.0050
        assertThat(result.distributions().get(0).perShare()).isEqualByComparingTo("0.005000");
        // 60.4 份 × 0.0050 = 0.302
        assertThat(result.amount()).isEqualByComparingTo("0.30");
    }

    /**
     * 多次除息要逐笔累加，金额按份额折算
     */
    @Test
    @DisplayName("多次除息逐笔累加")
    void sumsMultipleDistributions() {
        LocalDate since = LocalDate.of(2026, 7, 31);
        List<FundNetWorthPoint> history = List.of(
                point("2026-07-31", "1.6208", "1.9508", "-0.3700"),
                point("2026-08-06", "1.5688", "1.9038", "0.1200"),
                point("2026-09-02", "1.6159", "1.9509", "-0.5400"),
                point("2026-09-03", "1.6092", "1.9492", "-0.1100"),
                point("2026-10-15", "1.6269", "1.9669", "-1.1900"),
                point("2026-10-16", "1.6277", "1.9727", "0.3600"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        assertThat(result.distributions()).hasSize(3);
        assertThat(result.distributions()).extracting(DividendCalculator.Distribution::date)
                .containsExactly(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 10, 16));
        // 每份 0.0050 + 0.0049 + 0.0051 = 0.0150，1000 份 = 15.00
        assertThat(result.amount()).isEqualByComparingTo("15.00");
    }

    /**
     * 建仓之前发生的除息不算这笔持仓的分红，起点之后的才算
     */
    @Test
    @DisplayName("建仓前的除息不计入")
    void excludesDistributionBeforeOpenDate() {
        LocalDate since = LocalDate.of(2026, 8, 10);
        List<FundNetWorthPoint> history = List.of(
                point("2026-07-31", "1.6208", "1.9508", "-0.3700"),
                point("2026-08-06", "1.5688", "1.9038", "0.1200"),
                point("2026-09-02", "1.6159", "1.9509", "-0.5400"),
                point("2026-09-03", "1.6092", "1.9492", "-0.1100"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("500"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        // 8-06 在 8-10 之前，只有 9-03 算：两口径差从 0.335 变成 0.340，每份 0.005
        assertThat(result.distributions()).hasSize(1);
        assertThat(result.distributions().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.amount()).isEqualByComparingTo("2.50");
    }

    /**
     * 红利再投时分红折成份额，不能再算一遍现金，否则与份额口径重复
     */
    @Test
    @DisplayName("红利再投不算现金分红")
    void reinvestReturnsZeroCash() {
        LocalDate since = LocalDate.of(2026, 7, 31);
        List<FundNetWorthPoint> history = List.of(
                point("2026-07-31", "1.6208", "1.9508", "-0.3700"),
                point("2026-08-06", "1.5688", "1.9038", "0.1200"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("60.4"), since, latest,
                history, DividendMethod.REINVEST);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("0");
        assertThat(result.note()).contains("红利再投");
        // 明细仍要列出除息日，只是金额不再计入现金
        assertThat(result.distributions()).hasSize(1);
    }

    /**
     * 净值回报与净值变动对不上的小额差异是数据舍入，不能当成分红
     */
    @Test
    @DisplayName("舍入噪声不被当成除息")
    void ignoresRoundingNoise() {
        LocalDate since = LocalDate.of(2026, 1, 5);
        List<FundNetWorthPoint> history = List.of(
                point("2026-01-05", "1.1415", "1.9881", "-0.0100"),
                // 回报给 0，净值却变了 0.0001，属源数据舍入，低于最小分红阈值
                point("2026-01-06", "1.1416", "1.9882", "0.0000"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        assertThat(result.distributions()).isEmpty();
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    /**
     * 缺累计净值时改用当日回报正推，同样能识别除息
     */
    @Test
    @DisplayName("缺累计净值时按回报反推识别除息")
    void fallsBackToEquityReturnWithoutAccumulated() {
        LocalDate since = LocalDate.of(2026, 7, 31);
        List<FundNetWorthPoint> history = List.of(
                noAccumulated("2026-08-05", "1.5719", "-1.1300"),
                noAccumulated("2026-08-06", "1.5688", "0.1200"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        assertThat(result.distributions()).hasSize(1);
        // 缺累计净值，走回报反推：1.5719 × (1 + 0.12%) = 1.573786，减实际 1.5688 得 0.004986
        assertThat(result.distributions().get(0).perShare()).isEqualByComparingTo("0.004986");
    }

    /**
     * 除息日当天买入的份额不享受该次分红，必须严格要求除息日晚于建仓日
     */
    @Test
    @DisplayName("建仓当天发生的除息不计入")
    void excludesDistributionOnOpenDate() {
        // 建仓日与除息日同为 09-24
        LocalDate since = LocalDate.of(2026, 9, 24);
        List<FundNetWorthPoint> history = List.of(
                point("2026-09-23", "1.3809", "1.7689", "-0.4000"),
                // 两口径差从 0.3880 变成 0.4080，当日除息每份 0.02
                point("2026-09-24", "1.3663", "1.7743", "0.3900"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("10.98"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        // 当天买入不享受当天除息
        assertThat(result.distributions()).isEmpty();
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    /**
     * 除息日早于建仓日一天就要计入，边界只能卡在「当天」
     */
    @Test
    @DisplayName("建仓次日起的除息正常计入")
    void includesDistributionDayAfterOpen() {
        LocalDate since = LocalDate.of(2026, 9, 23);
        List<FundNetWorthPoint> history = List.of(
                point("2026-09-23", "1.3809", "1.7689", "-0.4000"),
                point("2026-09-24", "1.3663", "1.7743", "0.3900"));
        FundNetWorthPoint latest = history.get(history.size() - 1);

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("79.27"), since, latest,
                history, DividendMethod.CASH);

        assertThat(result).isNotNull();
        assertThat(result.distributions()).hasSize(1);
        assertThat(result.distributions().get(0).perShare()).isEqualByComparingTo("0.020000");
        // 79.27 × 0.02 = 1.5854 → 1.59
        assertThat(result.amount()).isEqualByComparingTo("1.59");
    }

    /**
     * 无净值序列时返回 null，页面据此不显示分红而不是显示 0
     */
    @Test
    @DisplayName("无净值序列时不推算")
    void noHistoryReturnsNull() {
        FundNetWorthPoint latest = point("2026-10-29", "1.6505", "1.9955", "1.4800");

        assertThat(calculator.calculate(new BigDecimal("1000"), LocalDate.of(2026, 7, 31), latest,
                List.of(), DividendMethod.CASH)).isNull();
    }

    /**
     * 只有一期净值时识别不出除息，返回 null 而不是 0
     */
    @Test
    @DisplayName("只有一期净值时不推算")
    void singlePointReturnsNull() {
        List<FundNetWorthPoint> history = List.of(point("2026-10-29", "1.6505", "1.9955", "1.4800"));

        assertThat(calculator.calculate(new BigDecimal("1000"), LocalDate.of(2026, 7, 31),
                history.get(0), history, DividendMethod.CASH)).isNull();
    }

    /**
     * 构造一个不带累计净值的点
     *
     * @param date         日期
     * @param unit         单位净值
     * @param equityReturn 当日净值回报，百分点
     * @return 净值点
     */
    private FundNetWorthPoint noAccumulated(String date, String unit, String equityReturn) {
        return new FundNetWorthPoint(LocalDate.parse(date), new BigDecimal(unit), new BigDecimal(equityReturn), null);
    }

    /**
     * 构造一个净值点
     *
     * @param date         日期
     * @param unit         单位净值
     * @param accumulated  累计净值
     * @param equityReturn 当日净值回报，百分点
     * @return 净值点
     */
    private FundNetWorthPoint point(String date, String unit, String accumulated, String equityReturn) {
        return new FundNetWorthPoint(LocalDate.parse(date), new BigDecimal(unit), new BigDecimal(equityReturn),
                new BigDecimal(accumulated));
    }
}
