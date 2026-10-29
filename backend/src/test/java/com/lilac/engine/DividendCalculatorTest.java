package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 现金分红推算测试，覆盖建仓后分红、无分红、建仓前分红不计入与数据缺失
 */
class DividendCalculatorTest {

    private final DividendCalculator calculator = new DividendCalculator();

    /**
     * 累计净值涨得比单位净值多，差额就是持仓期间分掉的现金
     */
    @Test
    @DisplayName("建仓后分红按累计净值与单位净值之差推算")
    void calculatesDividendSinceOpenDate() {
        LocalDate since = LocalDate.of(2026, 1, 5);
        List<FundNetWorthPoint> history = List.of(
                point("2026-01-05", "1.0000", "1.2000"),
                point("2026-01-06", "1.0100", "1.2100"));
        // 单位净值涨 0.0500、累计涨 0.0900，差 0.0400 是现金分红
        FundNetWorthPoint latest = point("2026-10-28", "1.0500", "1.2900");

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest, history);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("40.00");
        assertThat(result.note()).contains("建仓以来");
    }

    /**
     * 建仓之前的分红不算进本次持仓，起点取建仓日当天而不是基金成立日
     */
    @Test
    @DisplayName("建仓前的分红不计入")
    void excludesDividendBeforeOpenDate() {
        LocalDate since = LocalDate.of(2026, 6, 1);
        List<FundNetWorthPoint> history = List.of(
                point("2025-01-02", "1.0000", "1.5000"),
                point("2026-06-01", "1.2000", "1.5000"));
        // 起点取 2026-06-01，此后单位与累计同步涨 0.1000，没有新分红
        FundNetWorthPoint latest = point("2026-10-28", "1.3000", "1.6000");

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest, history);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("0");
        assertThat(result.note()).contains("无现金分红");
    }

    /**
     * 建仓日无净值时往前找最近一期，不因为差一天就判成算不出
     */
    @Test
    @DisplayName("建仓日无净值时向前取最近一期")
    void fallsBackToPreviousTradingDay() {
        LocalDate since = LocalDate.of(2026, 6, 1);
        List<FundNetWorthPoint> history = List.of(
                point("2026-05-29", "1.0000", "1.0000"),
                point("2026-06-02", "1.0100", "1.0100"));
        FundNetWorthPoint latest = point("2026-10-28", "1.0500", "1.0800");

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("500"), since, latest, history);

        // 单位涨 0.0500、累计涨 0.0800，差 0.0300 × 500
        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("15.00");
    }

    /**
     * 无累计净值数据时返回 null，页面据此不显示分红而不是显示 0
     */
    @Test
    @DisplayName("无累计净值时不推算")
    void noAccumulatedDataReturnsNull() {
        LocalDate since = LocalDate.of(2026, 1, 5);
        List<FundNetWorthPoint> history = List.of(point("2026-01-05", "1.0000", null));
        FundNetWorthPoint latest = point("2026-10-28", "1.0500", null);

        assertThat(calculator.calculate(new BigDecimal("1000"), since, latest, history)).isNull();
    }

    /**
     * 累计净值与单位净值口径不同步算出负数时按 0 处理，不给错误的分红
     */
    @Test
    @DisplayName("算出负数时按 0 处理")
    void negativeResultClampedToZero() {
        LocalDate since = LocalDate.of(2026, 1, 5);
        List<FundNetWorthPoint> history = List.of(point("2026-01-05", "1.0000", "1.0000"));
        FundNetWorthPoint latest = point("2026-10-28", "1.1000", "1.0500");

        DividendCalculator.Result result = calculator.calculate(new BigDecimal("1000"), since, latest, history);

        assertThat(result).isNotNull();
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    /**
     * 构造一个净值点
     *
     * @param date       日期
     * @param unit       单位净值
     * @param accumulated 累计净值，可为 null
     * @return 净值点
     */
    private FundNetWorthPoint point(String date, String unit, String accumulated) {
        return new FundNetWorthPoint(LocalDate.parse(date), new BigDecimal(unit), null,
                accumulated == null ? null : new BigDecimal(accumulated));
    }
}
