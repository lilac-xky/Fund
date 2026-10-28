package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.engine.model.GoldBetaModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 黄金 β 估计测试，覆盖 β 落在实测区间、逐日滚动与样本不足退化
 */
class GoldBetaEstimatorTest {

    private GoldBetaEstimator estimator;
    private final List<MarketBar> proxyBars = new ArrayList<>();
    private final List<FundNetWorthPoint> netWorths = new ArrayList<>();

    /**
     * 造一段「基金收益 = 0.91 × 代理收益」的严格线性序列
     */
    @BeforeEach
    void setUp() {
        estimator = new GoldBetaEstimator();
        double[] proxyReturns = {0.40, -0.55, 0.82, -0.12, 0.30, -0.75, 1.10, -0.33, 0.28, 0.66,
                -0.44, 0.15, -0.88, 0.51, 0.22, -0.19, 0.73, -0.61, 0.34, 0.09};
        double close = 5.00;
        LocalDate date = LocalDate.of(2026, 9, 1);
        proxyBars.add(new MarketBar(date, BigDecimal.valueOf(close)));
        for (int i = 0; i < proxyReturns.length; i++) {
            close = close * (1 + proxyReturns[i] / 100);
            date = date.plusDays(1);
            proxyBars.add(new MarketBar(date, BigDecimal.valueOf(close).setScale(4, java.math.RoundingMode.HALF_UP)));
            netWorths.add(new FundNetWorthPoint(date, BigDecimal.valueOf(1.5),
                    BigDecimal.valueOf(proxyReturns[i] * 0.91)));
        }
        // 首日净值对应代理的第一根日线，收益对齐用
        netWorths.add(0, new FundNetWorthPoint(LocalDate.of(2026, 9, 1), BigDecimal.valueOf(1.5), BigDecimal.ZERO));
    }

    /**
     * 基金按代理的 0.91 倍跟随，估出的 β 应接近实测的 0.90~0.92
     */
    @Test
    @DisplayName("β 落在实测区间 0.90~0.92")
    void betaInMeasuredRange() {
        GoldBetaModel model = estimator.estimate(netWorths, proxyBars, 30, 10);

        assertThat(model.isFallback()).isFalse();
        assertThat(model.getBeta()).isBetween(new BigDecimal("0.90"), new BigDecimal("0.92"));
        assertThat(model.getAlpha().abs()).isLessThan(new BigDecimal("0.01"));
    }

    /**
     * 两只联接基金各按不同现金比例跟随，β 必须分别估计且不相等
     */
    @Test
    @DisplayName("两只联接基金分别估 β 且不相等")
    void betaEstimatedPerFund() {
        List<FundNetWorthPoint> first = scale(netWorths, new BigDecimal("0.9175"));
        List<FundNetWorthPoint> second = scale(netWorths, new BigDecimal("0.9109"));

        BigDecimal betaFirst = estimator.estimate(first, proxyBars, 30, 10).getBeta();
        BigDecimal betaSecond = estimator.estimate(second, proxyBars, 30, 10).getBeta();

        assertThat(betaFirst).isNotEqualByComparingTo(betaSecond);
        assertThat(betaFirst).isBetween(new BigDecimal("0.90"), new BigDecimal("0.93"));
        assertThat(betaSecond).isBetween(new BigDecimal("0.90"), new BigDecimal("0.92"));
    }

    /**
     * 只用窗口内的最近样本，前面的数据不参与，保证严格前向无前视
     */
    @Test
    @DisplayName("只滚动最近窗口的样本")
    void rollingWindowOnly() {
        GoldBetaModel shortWindow = estimator.estimate(netWorths, proxyBars, 5, 3);
        GoldBetaModel longWindow = estimator.estimate(netWorths, proxyBars, 30, 10);

        // 首日净值没有对应的代理前值，配对从第二天起算，共 20 个样本
        assertThat(shortWindow.getSamples()).isEqualTo(5);
        assertThat(longWindow.getSamples()).isEqualTo(20);
    }

    /**
     * 样本不足时退化为 1 倍直取，并标记 fallback 供文案提示
     */
    @Test
    @DisplayName("样本不足退化为直取")
    void fallsBackWhenTooFewPairs() {
        GoldBetaModel model = estimator.estimate(netWorths.subList(0, 4), proxyBars, 30, 10);

        assertThat(model.isFallback()).isTrue();
        assertThat(model.getBeta()).isEqualByComparingTo("1");
        assertThat(model.predict(new BigDecimal("1.00"))).isEqualByComparingTo("1.00000000");
    }

    /**
     * AR(1) 项取上一期残差，ρ 被限制在 ±0.9 内
     */
    @Test
    @DisplayName("AR(1) 吸收上一期残差且 ρ 有上限")
    void arTermBounded() {
        List<FundNetWorthPoint> biased = new ArrayList<>();
        for (int i = 0; i < netWorths.size(); i++) {
            FundNetWorthPoint point = netWorths.get(i);
            // 交替正负的残差会产生强负自相关
            BigDecimal drift = i % 2 == 0 ? new BigDecimal("0.20") : new BigDecimal("-0.20");
            biased.add(new FundNetWorthPoint(point.getNetWorthDate(), point.getUnitNetWorth(),
                    point.getEquityReturn().add(drift)));
        }

        GoldBetaModel model = estimator.estimate(biased, proxyBars, 30, 10);

        assertThat(model.getRho()).isNotNull();
        assertThat(model.getRho().abs()).isLessThanOrEqualTo(new BigDecimal("0.9"));
        assertThat(model.getRho()).isLessThan(BigDecimal.ZERO);
    }

    /**
     * 按给定系数重新缩放净值回报
     *
     * @param source 原始序列
     * @param ratio  目标系数
     * @return 缩放后的序列
     */
    private List<FundNetWorthPoint> scale(List<FundNetWorthPoint> source, BigDecimal ratio) {
        List<FundNetWorthPoint> scaled = new ArrayList<>(source.size());
        for (FundNetWorthPoint point : source) {
            scaled.add(new FundNetWorthPoint(point.getNetWorthDate(), point.getUnitNetWorth(),
                    point.getEquityReturn().multiply(ratio).divide(new BigDecimal("0.91"), 6,
                            java.math.RoundingMode.HALF_UP)));
        }
        return scaled;
    }
}
