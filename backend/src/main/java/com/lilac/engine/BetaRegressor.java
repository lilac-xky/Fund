package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.engine.model.BetaModel;
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
 * 代理标的 β 回归器，用基金净值与代理日线对齐后估 α 与 β，只用当日之前的数据
 */
@Slf4j
@Component
public class BetaRegressor {

    private static final int SCALE = 8;
    private static final int OUTPUT_SCALE = 6;

    /**
     * 滚动估计 α 与 β，样本不足时返回 null 交由调用方退回直取
     *
     * @param netWorths 基金历史净值，按日期升序
     * @param proxyBars 代理日线，按日期升序
     * @param window    滚动窗口，交易日
     * @param minPairs  最少配对样本
     * @return 回归结果，样本不足返回 null
     */
    public BetaModel estimate(List<FundNetWorthPoint> netWorths, List<MarketBar> proxyBars, int window, int minPairs) {
        List<BigDecimal[]> pairs = pairReturns(netWorths, proxyBars);
        int need = minPairs <= 0 ? 1 : minPairs;
        if (pairs.size() < need) {
            log.info("β 回归样本不足 {} < {}，退回直取代理", pairs.size(), need);
            return null;
        }
        List<BigDecimal[]> used = window > 0 && pairs.size() > window
                ? pairs.subList(pairs.size() - window, pairs.size()) : pairs;
        double[] xs = new double[used.size()];
        double[] ys = new double[used.size()];
        for (int i = 0; i < used.size(); i++) {
            xs[i] = used.get(i)[0].doubleValue();
            ys[i] = used.get(i)[1].doubleValue();
        }
        double variance = variance(xs);
        if (variance == 0) {
            // 代理在这段窗口里完全没有波动，回归斜率没有意义
            log.info("代理标的本窗口零波动，β 回归跳过");
            return null;
        }
        double beta = covariance(xs, ys) / variance;
        double alpha = mean(ys) - beta * mean(xs);
        BetaModel model = new BetaModel();
        model.setBeta(scale(beta));
        model.setAlpha(scale(alpha));
        model.setSamples(used.size());
        log.info("β 回归完成 样本={} β={} α={}", used.size(), model.getBeta(), model.getAlpha());
        return model;
    }

    /**
     * 按交易日对齐基金净值回报与代理日线收益
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
