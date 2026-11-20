package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.engine.model.BetaModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代理标的 β 回归测试，核心是「只在样本足够时回归」与「样本不足退回直取」
 */
class BetaRegressorTest {

    private BetaRegressor regressor;

    /**
     * 装配回归器
     */
    @BeforeEach
    void setUp() {
        regressor = new BetaRegressor();
    }

    /**
     * 基金收益恒为代理收益的 0.9 倍时，β 应回归到 0.9、α 接近 0
     */
    @Test
    @DisplayName("按构造比例回归出 β 与 α")
    void recoversBetaAndAlpha() {
        List<MarketBar> bars = bars(120);
        List<FundNetWorthPoint> netWorths = netWorthsOf(bars, 0.9, 0.0);

        BetaModel model = regressor.estimate(netWorths, bars, 250, 60);

        assertThat(model).isNotNull();
        assertThat(model.getBeta()).isEqualByComparingTo("0.900000");
        assertThat(model.getAlpha().abs()).isLessThan(new BigDecimal("0.0001"));
        assertThat(model.getSamples()).isEqualTo(119);
    }

    /**
     * 有固定截距时 α 应回归出来，预测值等于 β 乘代理加 α
     */
    @Test
    @DisplayName("带截距时 α 参与预测")
    void alphaParticipatesInPrediction() {
        List<MarketBar> bars = bars(120);
        List<FundNetWorthPoint> netWorths = netWorthsOf(bars, 1.0, 0.02);

        BetaModel model = regressor.estimate(netWorths, bars, 250, 60);

        assertThat(model).isNotNull();
        assertThat(model.getAlpha()).isEqualByComparingTo("0.020000");
        // 1.00 × 1.50 + 0.02 = 1.52
        assertThat(model.predict(new BigDecimal("1.50"))).isEqualByComparingTo("1.520000");
    }

    /**
     * 配对样本少于下限时返回 null，调用方据此退回直接沿用代理涨跌
     */
    @Test
    @DisplayName("样本不足返回 null 退回直取")
    void returnsNullWhenSamplesFew() {
        List<MarketBar> bars = bars(30);
        List<FundNetWorthPoint> netWorths = netWorthsOf(bars, 0.9, 0.0);

        assertThat(regressor.estimate(netWorths, bars, 250, 60)).isNull();
    }

    /**
     * 代理标的零波动时斜率没有意义，返回 null 而不是给出一个凭空的 β
     */
    @Test
    @DisplayName("代理零波动时返回 null")
    void returnsNullWhenProxyFlat() {
        List<MarketBar> bars = flatBars(120);
        List<FundNetWorthPoint> netWorths = netWorthsOf(bars, 0.9, 0.0);

        assertThat(regressor.estimate(netWorths, bars, 250, 10)).isNull();
    }

    /**
     * 构造涨跌交替的代理日线，避免收益率恒定时回归退化
     *
     * @param count 根数
     * @return 日线序列
     */
    private List<MarketBar> bars(int count) {
        List<MarketBar> bars = new ArrayList<>(count);
        double close = 100.0;
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            bars.add(new MarketBar(date.plusDays(i), BigDecimal.valueOf(round(close))));
            // 每日涨跌幅在 ±1% 到 ±2% 之间交替，保证自变量有方差
            double rate = (i % 2 == 0 ? 1 : -1) * (0.01 + 0.01 * (i % 5) / 4.0);
            close = close * (1 + rate);
        }
        return bars;
    }

    /**
     * 构造代理零波动的日线，用于验证不给出凭空斜率
     *
     * @param count 根数
     * @return 日线序列
     */
    private List<MarketBar> flatBars(int count) {
        List<MarketBar> bars = new ArrayList<>(count);
        LocalDate date = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < count; i++) {
            bars.add(new MarketBar(date.plusDays(i), BigDecimal.valueOf(100.0)));
        }
        return bars;
    }

    /**
     * 按给定 β 与 α 从代理日线造出基金净值收益序列
     *
     * @param bars      代理日线
     * @param beta      仓位系数
     * @param alpha     截距，百分点
     * @return 净值序列
     */
    private List<FundNetWorthPoint> netWorthsOf(List<MarketBar> bars, double beta, double alpha) {
        List<FundNetWorthPoint> points = new ArrayList<>(bars.size());
        for (int i = 1; i < bars.size(); i++) {
            BigDecimal previous = bars.get(i - 1).getClose();
            BigDecimal current = bars.get(i).getClose();
            double proxyReturn = current.subtract(previous)
                    .divide(previous, 8, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
            FundNetWorthPoint point = new FundNetWorthPoint();
            point.setNetWorthDate(bars.get(i).getDate());
            point.setEquityReturn(BigDecimal.valueOf(round(beta * proxyReturn + alpha)));
            points.add(point);
        }
        return points;
    }

    /**
     * 保留六位小数，避免构造数据带出浮点尾差
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
