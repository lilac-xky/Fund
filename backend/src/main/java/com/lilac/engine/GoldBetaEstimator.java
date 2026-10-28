package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.engine.model.GoldBetaModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄金联接基金 β 与 AR(1) 的滚动估计器，只用当日之前的数据，避免前视偏差
 */
@Slf4j
@Component
public class GoldBetaEstimator {

    private static final BigDecimal RHO_LIMIT = new BigDecimal("0.9");
    private static final int SCALE = 8;
    private static final int OUTPUT_SCALE = 6;

    /**
     * 滚动估计 β、α 与 ρ，样本不足时退化为直取代理
     *
     * @param netWorths  基金历史净值，按日期升序
     * @param proxyBars  黄金 ETF 日线，按日期升序
     * @param window     滚动窗口长度，即最近多少个配对交易日
     * @param minPairs   最少配对样本，不足则退化
     * @return β 模型
     */
    public GoldBetaModel estimate(List<FundNetWorthPoint> netWorths, List<MarketBar> proxyBars,
                                 int window, int minPairs) {
        GoldBetaModel model = new GoldBetaModel();
        List<BigDecimal[]> pairs = pairReturns(netWorths, proxyBars);
        if (pairs.size() < minPairs) {
            // 样本不足时不做回归，按 1 倍代理直取，并明确标记这是退化结果
            log.warn("黄金 β 样本不足 {} < {}，退化为代理直取", pairs.size(), minPairs);
            model.setBeta(BigDecimal.ONE);
            model.setAlpha(BigDecimal.ZERO);
            model.setSamples(pairs.size());
            model.setFallback(true);
            return model;
        }
        List<BigDecimal[]> used = window > 0 && pairs.size() > window
                ? pairs.subList(pairs.size() - window, pairs.size()) : pairs;
        double[] xs = new double[used.size()];
        double[] ys = new double[used.size()];
        for (int i = 0; i < used.size(); i++) {
            xs[i] = used.get(i)[0].doubleValue();
            ys[i] = used.get(i)[1].doubleValue();
        }
        double beta = covariance(xs, ys) / variance(xs);
        double alpha = mean(ys) - beta * mean(xs);
        double[] residuals = new double[ys.length];
        for (int i = 0; i < ys.length; i++) {
            residuals[i] = ys[i] - (alpha + beta * xs[i]);
        }
        model.setBeta(scale(beta));
        model.setAlpha(scale(alpha));
        model.setSamples(used.size());
        model.setRho(autocorrelation(residuals));
        model.setLastResidual(scale(residuals[residuals.length - 1]));
        model.setFallback(false);
        log.info("黄金 β 估计完成 样本={} β={} ρ={}", used.size(), model.getBeta(), model.getRho());
        return model;
    }

    /**
     * 按交易日对齐基金净值回报与代理日线收益，只用当天及之前的数据
     *
     * @param netWorths 基金净值
     * @param proxyBars 代理日线
     * @return 每项为 [代理收益, 基金收益]，单位百分点
     */
    private List<BigDecimal[]> pairReturns(List<FundNetWorthPoint> netWorths, List<MarketBar> proxyBars) {
        List<BigDecimal[]> pairs = new ArrayList<>();
        if (netWorths == null || proxyBars == null || netWorths.isEmpty() || proxyBars.size() < 2) {
            return pairs;
        }
        Map<LocalDate, BigDecimal> proxyReturns = new LinkedHashMap<>();
        for (int i = 1; i < proxyBars.size(); i++) {
            BigDecimal previous = proxyBars.get(i - 1).getClose();
            BigDecimal current = proxyBars.get(i).getClose();
            if (previous == null || current == null || previous.signum() == 0) {
                continue;
            }
            proxyReturns.put(proxyBars.get(i).getDate(), current.subtract(previous)
                    .divide(previous, SCALE, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));
        }
        for (FundNetWorthPoint point : netWorths) {
            BigDecimal fundReturn = point.getEquityReturn();
            BigDecimal proxyReturn = proxyReturns.get(point.getNetWorthDate());
            if (fundReturn == null || proxyReturn == null) {
                continue;
            }
            pairs.add(new BigDecimal[]{proxyReturn, fundReturn});
        }
        return pairs;
    }

    /**
     * 计算一阶自相关，分母为残差方差，结果限制在 ±0.9 内防止爆掉
     *
     * @param residuals 残差序列
     * @return ρ，样本不足 3 个时返回 null
     */
    private BigDecimal autocorrelation(double[] residuals) {
        if (residuals.length < 3) {
            return null;
        }
        double denominator = 0;
        double numerator = 0;
        for (int i = 0; i < residuals.length; i++) {
            denominator += residuals[i] * residuals[i];
            if (i > 0) {
                numerator += residuals[i - 1] * residuals[i];
            }
        }
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        double rho = numerator / denominator;
        double clamped = Math.max(-RHO_LIMIT.doubleValue(), Math.min(RHO_LIMIT.doubleValue(), rho));
        return BigDecimal.valueOf(clamped).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 求均值
     *
     * @param values 序列
     * @return 均值
     */
    private double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * 求总体方差
     *
     * @param values 序列
     * @return 方差
     */
    private double variance(double[] values) {
        double average = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - average) * (value - average);
        }
        return sum / values.length;
    }

    /**
     * 求协方差
     *
     * @param xs 自变量
     * @param ys 因变量
     * @return 协方差
     */
    private double covariance(double[] xs, double[] ys) {
        double meanX = mean(xs);
        double meanY = mean(ys);
        double sum = 0;
        for (int i = 0; i < xs.length; i++) {
            sum += (xs[i] - meanX) * (ys[i] - meanY);
        }
        return sum / xs.length;
    }

    /**
     * 统一保留六位小数
     *
     * @param value 原始值
     * @return 保留后的值
     */
    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
}
