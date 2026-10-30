package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 债券预估修正器，把债券 ETF 代理涨跌换成该基金自己的日波动量级
 */
@Slf4j
@Component
public class BondBaselineEstimator {

    private static final int SCALE = 8;
    private static final int OUTPUT_SCALE = 6;
    private static final int MIN_PAIRS = 10;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 估计基金对债券代理的传导系数，即基金日收益 / 代理日收益的稳健中位数
     *
     * @param netWorths 基金历史净值，按日期升序
     * @param proxyBars 债券 ETF 日线，按日期升序
     * @param window    回看窗口，交易日
     * @param minPairs  最少配对样本
     * @return 传导系数，样本不足返回 null
     */
    public Model estimate(List<FundNetWorthPoint> netWorths, List<MarketBar> proxyBars, int window, int minPairs) {
        List<BigDecimal[]> pairs = pairReturns(netWorths, proxyBars);
        int need = minPairs <= 0 ? MIN_PAIRS : minPairs;
        if (pairs.size() < need) {
            log.info("债券传导系数样本不足 {} < {}，退化为按 1 倍代理", pairs.size(), need);
            return null;
        }
        List<BigDecimal[]> used = window > 0 && pairs.size() > window
                ? pairs.subList(pairs.size() - window, pairs.size()) : pairs;
        // 代理涨跌接近 0 时比值会被放大成噪声，只取代理波动够大的样本
        List<BigDecimal> ratios = new ArrayList<>();
        for (BigDecimal[] pair : used) {
            BigDecimal proxy = pair[0];
            if (proxy.abs().compareTo(new BigDecimal("0.01")) < 0) {
                continue;
            }
            ratios.add(pair[1].divide(proxy, SCALE, RoundingMode.HALF_UP));
        }
        if (ratios.size() < need) {
            log.info("债券传导系数有效样本不足 {} < {}，退化为按 1 倍代理", ratios.size(), need);
            return null;
        }
        ratios.sort(BigDecimal::compareTo);
        BigDecimal median = ratios.get(ratios.size() / 2);
        Model model = new Model(median.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP),
                meanAbs(used, 1), used.size(), false);
        log.info("债券传导系数估计完成 样本={} 系数={} 基金日均波动={}", used.size(), model.transmission(),
                model.fundMeanAbsChange());
        return model;
    }

    /**
     * 用传导系数把代理涨跌换成该基金的估计涨跌，代理波动过大时按基金自己的量级截断
     *
     * @param model       传导模型
     * @param proxyChange 代理标的当日涨跌幅，百分点
     * @return 预估涨跌幅，百分点
     */
    public BigDecimal predict(Model model, BigDecimal proxyChange) {
        if (model == null || proxyChange == null) {
            return proxyChange;
        }
        BigDecimal value = proxyChange.multiply(model.transmission()).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        // 债券单日涨跌极少超过自身波动的一个量级，超出说明代理当日有异动，不能照搬
        BigDecimal cap = model.fundMeanAbsChange().multiply(BigDecimal.valueOf(4));
        if (cap.signum() > 0 && value.abs().compareTo(cap) > 0) {
            return value.signum() > 0 ? cap.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP)
                    : cap.negate().setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
        }
        return value;
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
        List<Object[]> proxyReturns = new ArrayList<>();
        for (int i = 1; i < proxyBars.size(); i++) {
            BigDecimal previous = proxyBars.get(i - 1).getClose();
            BigDecimal current = proxyBars.get(i).getClose();
            LocalDate date = proxyBars.get(i).getDate();
            if (previous == null || current == null || previous.signum() == 0 || date == null) {
                continue;
            }
            proxyReturns.add(new Object[]{date, current.subtract(previous)
                    .divide(previous, SCALE, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))});
        }
        for (FundNetWorthPoint point : netWorths) {
            if (point.getEquityReturn() == null) {
                continue;
            }
            for (Object[] row : proxyReturns) {
                if (row[0].equals(point.getNetWorthDate())) {
                    pairs.add(new BigDecimal[]{((BigDecimal) row[1]), point.getEquityReturn()});
                    break;
                }
            }
        }
        return pairs;
    }

    /**
     * 取基金日均绝对涨跌，用于给估算值封顶
     *
     * @param pairs  配对样本
     * @param column 列下标，1 为基金收益
     * @return 日均绝对涨跌，百分点
     */
    private BigDecimal meanAbs(List<BigDecimal[]> pairs, int column) {
        if (pairs.isEmpty()) {
            return ZERO;
        }
        BigDecimal sum = ZERO;
        for (BigDecimal[] pair : pairs) {
            sum = sum.add(pair[column].abs());
        }
        return sum.divide(BigDecimal.valueOf(pairs.size()), OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 债券传导模型，系数与基金自身波动量级一起给出
     */
    public record Model(BigDecimal transmission, BigDecimal fundMeanAbsChange, int samples, boolean fallback) {
    }
}
