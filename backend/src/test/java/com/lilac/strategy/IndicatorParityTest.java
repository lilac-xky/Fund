package com.lilac.strategy;

import com.lilac.adapter.model.MarketBar;
import com.lilac.strategy.model.IndicatorPlan;
import com.lilac.strategy.model.IndicatorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标与公开口径的对比校验，用沪深300ETF 真实前复权日线核对计算是否与常见行情软件一致
 */
class IndicatorParityTest {

    private static final Path SAMPLE = Path.of("src/test/resources/bars_510300.csv");

    /**
     * 用真实日线复算，三指标须落在公开口径附近；偏差只允许来自 EMA 种子与精度处理
     */
    @Test
    @DisplayName("真实日线算出的三指标与公开口径一致")
    void matchesPublicQuotes() throws IOException {
        List<MarketBar> bars = load();
        assertThat(bars).hasSize(120);
        assertThat(bars.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(bars.get(119).getDate()).isEqualTo(LocalDate.of(2026, 10, 30));

        IndicatorResult result = new IndicatorCalculator().compute(bars, plan());
        assertThat(result.complete()).isTrue();

        // 对照值由同一份日线按业内通用公式独立算出：RSI 24.0482、DIF −0.1142、DEA −0.1077、K 29.1604、D 30.2224
        assertThat(result.getRsi()).isBetween(new BigDecimal("23.9"), new BigDecimal("24.2"));
        assertThat(result.getDif()).isBetween(new BigDecimal("-0.12"), new BigDecimal("-0.11"));
        assertThat(result.getDea()).isBetween(new BigDecimal("-0.11"), new BigDecimal("-0.10"));
        assertThat(result.getK()).isBetween(new BigDecimal("28.8"), new BigDecimal("29.5"));
        assertThat(result.getD()).isBetween(new BigDecimal("29.9"), new BigDecimal("30.5"));

        // 该区间末段连续下跌，RSI 应低于超卖阈值并触发超卖信号
        assertThat(result.getRsi()).isLessThan(new BigDecimal("30"));
        assertThat(result.getSignals()).anyMatch(signal -> signal.getLabel().contains("RSI 超卖"));
    }

    /**
     * 参数改动后同一份日线的结果必须变化，验证参数真的进了计算
     */
    @Test
    @DisplayName("真实日线上改参数指标随之变化")
    void paramsAffectRealData() throws IOException {
        List<MarketBar> bars = load();
        IndicatorCalculator calculator = new IndicatorCalculator();

        IndicatorResult base = calculator.compute(bars, plan());
        IndicatorPlan wider = plan();
        wider.setRsiPeriod(7);
        wider.setMacdFast(6);
        wider.setMacdSlow(13);
        wider.setMacdSignal(5);
        IndicatorResult changed = calculator.compute(bars, wider);

        assertThat(changed.getRsi()).isNotEqualByComparingTo(base.getRsi());
        assertThat(changed.getDif()).isNotEqualByComparingTo(base.getDif());
    }

    /**
     * 读取真实日线样本，列为日期、开、收、高、低、量
     *
     * @return 日线序列
     * @throws IOException 读取失败
     */
    private List<MarketBar> load() throws IOException {
        List<MarketBar> bars = new ArrayList<>();
        for (String line : Files.readAllLines(SAMPLE, StandardCharsets.UTF_8)) {
            String[] cells = line.replace("\"", "").split(",");
            if (cells.length < 6) {
                continue;
            }
            bars.add(new MarketBar(LocalDate.parse(cells[0]), new BigDecimal(cells[1]),
                    new BigDecimal(cells[2]), new BigDecimal(cells[3]), new BigDecimal(cells[4]),
                    new BigDecimal(cells[5])));
        }
        bars.sort((left, right) -> left.getDate().compareTo(right.getDate()));
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
