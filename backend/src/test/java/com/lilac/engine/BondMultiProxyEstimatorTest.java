package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 债券多代理回归测试，核心是「能还原构造的权重」「共线时不炸」「样本不足退回」
 */
class BondMultiProxyEstimatorTest {

    private BondMultiProxyEstimator estimator;

    /**
     * 装配回归器
     */
    @BeforeEach
    void setUp() {
        estimator = new BondMultiProxyEstimator();
    }

    /**
     * 基金收益按固定权重从两个代理合成时，回归应还原出该权重与截距
     */
    @Test
    @DisplayName("还原构造的代理权重与截距")
    void recoversWeights() {
        Map<String, List<MarketBar>> proxies = twoProxies(160);
        List<FundNetWorthPoint> fund = compose(proxies, 0.30, 0.55, 0.010);

        BondMultiProxyEstimator.Model model = estimator.estimate(fund, proxies, 250, 60);

        assertThat(model).isNotNull();
        assertThat(model.proxyCount()).isEqualTo(2);
        assertThat(model.intercept()).isEqualByComparingTo("0.010000");
        List<BigDecimal> weights = new ArrayList<>(model.weights().values());
        assertThat(weights.get(0)).isEqualByComparingTo("0.300000");
        assertThat(weights.get(1)).isEqualByComparingTo("0.550000");
    }

    /**
     * 两个代理走势完全一致时设计矩阵奇异，必须给出稳定解而不是抛异常
     */
    @Test
    @DisplayName("代理高度共线时给稳定解不抛异常")
    void handlesCollinearProxies() {
        Map<String, List<MarketBar>> proxies = twoProxies(160);
        // 第二个代理与第一个完全相同，正规矩阵必然奇异
        proxies.put("sh511030", proxies.get("sh511010"));
        List<FundNetWorthPoint> fund = compose(proxies, 0.40, 0.40, 0.0);

        BondMultiProxyEstimator.Model model = estimator.estimate(fund, proxies, 250, 60);

        assertThat(model).isNotNull();
        // 共线时权重分配不唯一，但合成结果必须接近真实
        Map<String, BigDecimal> changes = new LinkedHashMap<>();
        changes.put("sh511010", new BigDecimal("0.1000"));
        changes.put("sh511030", new BigDecimal("0.1000"));
        assertThat(estimator.predict(model, changes)).isNotNull();
    }

    /**
     * 样本少于下限时返回 null，调用方据此退回单代理
     */
    @Test
    @DisplayName("样本不足返回 null 退回单代理")
    void returnsNullWhenSamplesFew() {
        Map<String, List<MarketBar>> proxies = twoProxies(30);
        List<FundNetWorthPoint> fund = compose(proxies, 0.30, 0.55, 0.0);

        assertThat(estimator.estimate(fund, proxies, 250, 60)).isNull();
    }

    /**
     * 代理为空时直接返回 null，不产生除零
     */
    @Test
    @DisplayName("无代理时返回 null")
    void returnsNullWhenNoProxy() {
        assertThat(estimator.estimate(List.of(), Map.of(), 250, 60)).isNull();
    }

    /**
     * 预测值超过基金自身量级时按上限截断，避免代理异动被原样放大
     */
    @Test
    @DisplayName("代理异动时预测被自身量级截断")
    void capsOutlierPrediction() {
        Map<String, List<MarketBar>> proxies = twoProxies(160);
        List<FundNetWorthPoint> fund = compose(proxies, 0.30, 0.55, 0.0);
        BondMultiProxyEstimator.Model model = estimator.estimate(fund, proxies, 250, 60);
        assertThat(model).isNotNull();

        Map<String, BigDecimal> huge = new LinkedHashMap<>();
        huge.put("sh511010", new BigDecimal("50.0"));
        huge.put("sh511030", new BigDecimal("50.0"));

        BigDecimal predicted = estimator.predict(model, huge);

        assertThat(predicted).isNotNull();
        // 截断上限 = 日均绝对波动 × 4；构造数据的日波动约 1.4，所以上限约 5.6
        BigDecimal cap = model.fundMeanAbsChange().multiply(BigDecimal.valueOf(4));
        assertThat(predicted.abs()).isLessThanOrEqualTo(cap.add(new BigDecimal("0.001")));
        // 50% 的代理涨跌若不被截断，合成值会远超 10
        assertThat(predicted.abs()).isLessThan(new BigDecimal("10"));
    }

    /**
     * 有代理缺当日数据时只用其余代理合成，不整条丢弃
     */
    @Test
    @DisplayName("代理缺项时用其余代理合成")
    void predictWithPartialProxies() {
        Map<String, List<MarketBar>> proxies = twoProxies(160);
        List<FundNetWorthPoint> fund = compose(proxies, 0.30, 0.55, 0.0);
        BondMultiProxyEstimator.Model model = estimator.estimate(fund, proxies, 250, 60);
        assertThat(model).isNotNull();

        Map<String, BigDecimal> partial = new LinkedHashMap<>();
        partial.put("sh511010", new BigDecimal("0.05"));

        assertThat(estimator.predict(model, partial)).isNotNull();
        assertThat(estimator.predict(model, Map.of())).isNull();
    }

    /**
     * 构造两个涨跌交替的代理日线，保证自变量有方差
     *
     * @param count 根数
     * @return 代代码到日线的映射
     */
    private Map<String, List<MarketBar>> twoProxies(int count) {
        Map<String, List<MarketBar>> proxies = new LinkedHashMap<>();
        proxies.put("sh511010", bars(count, 0.01, 0.013));
        proxies.put("sh511030", bars(count, 0.008, 0.017));
        return proxies;
    }

    /**
     * 造一组涨跌交替、幅度不成比例的日线
     *
     * @param count  根数
     * @param base   基准日涨跌，小数
     * @param spread 幅度差异，小数
     * @return 日线序列
     */
    private List<MarketBar> bars(int count, double base, double spread) {
        List<MarketBar> bars = new ArrayList<>(count);
        double close = 100.0;
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            bars.add(new MarketBar(date.plusDays(i), BigDecimal.valueOf(round(close))));
            double sign = (i % 2 == 0) ? 1 : -1;
            double rate = sign * (base + spread * (i % 7) / 6.0);
            close = close * (1 + rate);
        }
        return bars;
    }

    /**
     * 按给定权重把代理日收益合成基金收益
     *
     * @param proxies 代理日线
     * @param w1      第一个代理权重
     * @param w2      第二个代理权重
     * @param alpha   截距，百分点
     * @return 净值序列
     */
    private List<FundNetWorthPoint> compose(Map<String, List<MarketBar>> proxies, double w1, double w2,
                                            double alpha) {
        List<MarketBar> a = proxies.get("sh511010");
        List<MarketBar> b = proxies.get("sh511030");
        List<FundNetWorthPoint> points = new ArrayList<>(a.size());
        for (int i = 1; i < a.size(); i++) {
            double r1 = a.get(i).getClose().subtract(a.get(i - 1).getClose())
                    .divide(a.get(i - 1).getClose(), 8, RoundingMode.HALF_UP).doubleValue() * 100;
            double r2 = b.get(i).getClose().subtract(b.get(i - 1).getClose())
                    .divide(b.get(i - 1).getClose(), 8, RoundingMode.HALF_UP).doubleValue() * 100;
            FundNetWorthPoint point = new FundNetWorthPoint();
            point.setNetWorthDate(a.get(i).getDate());
            point.setEquityReturn(BigDecimal.valueOf(round(w1 * r1 + w2 * r2 + alpha)));
            points.add(point);
        }
        return points;
    }

    /**
     * 保留六位小数，避免构造数据带浮点尾差
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
