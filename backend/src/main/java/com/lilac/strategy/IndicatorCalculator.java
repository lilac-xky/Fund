package com.lilac.strategy;

import com.lilac.adapter.model.MarketBar;
import com.lilac.enums.IndicatorSignal;
import com.lilac.strategy.model.IndicatorPlan;
import com.lilac.strategy.model.IndicatorResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标计算器，纯计算不访问数据库，按使用者设定的参数算出 RSI、KDJ、MACD 并给出信号
 */
@Slf4j
@Component
public class IndicatorCalculator {

    private static final int HUNDRED = 100;
    private static final int RESULT_SCALE = 4;
    private static final MathContext CONTEXT = MathContext.DECIMAL64;

    /**
     * 零值常量，Wilder 平滑与 KDJ 递推反复用到
     */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 百分比基数，RSI 与 RSV 的换算都用它
     */
    private static final BigDecimal HUNDRED_DECIMAL = BigDecimal.valueOf(HUNDRED);

    /**
     * 按日线序列计算三个指标并判定信号，数据不足时明确说明
     *
     * @param bars 日线序列，按日期升序
     * @param plan 指标参数
     * @return 指标结果
     */
    public IndicatorResult compute(List<MarketBar> bars, IndicatorPlan plan) {
        IndicatorResult result = new IndicatorResult();
        if (plan == null || !plan.complete()) {
            if (plan != null && plan.getIssues() != null) {
                result.getRemarks().addAll(plan.getIssues());
            }
            return unavailable(result, "指标参数缺失或不自洽，暂不计算技术指标");
        }
        List<MarketBar> usable = usable(bars);
        // 三个指标所需根数不同，够算哪个就先出哪个，不因为 MACD 缺根数把 RSI 也一起废掉
        int need = requiredBars(plan);
        if (usable.size() < Math.max(plan.getRsiPeriod() + 1, plan.getKdjPeriod() + 1)) {
            return unavailable(result, "可用日线仅 " + usable.size() + " 根，按当前参数至少要 "
                    + Math.max(plan.getRsiPeriod() + 1, plan.getKdjPeriod() + 1) + " 根才能算出指标");
        }
        result.setSamples(usable.size());
        MarketBar latest = usable.get(usable.size() - 1);
        result.setBarDate(latest.getDate());
        computeRsi(result, usable, plan);
        computeKdj(result, usable, plan);
        computeMacd(result, usable, plan);
        // 只在根数真的不够时才提根数，缺高低价导致的缺失由 computeKdj 自己说明
        if (!result.complete() && usable.size() < need) {
            result.getRemarks().add("仅有 " + usable.size() + " 个交易日数据，按当前参数算出全部三个指标需要 "
                    + need + " 个；缺的指标暂不显示");
        }
        fillSignals(result, plan);
        return result;
    }

    /**
     * 按当前参数算出最少需要多少根日线，三个指标取最大值再留一根用于比较上一根
     *
     * @param plan 指标参数
     * @return 根数
     */
    public int requiredBars(IndicatorPlan plan) {
        if (plan == null) {
            return Integer.MAX_VALUE;
        }
        int macdNeed = Math.max(plan.getMacdSlow(), plan.getMacdSignal()) + plan.getMacdFast();
        int kdjNeed = plan.getKdjPeriod() + 1;
        // RSI 需要 period 个涨跌，即 period+1 根；KDJ 与 MACD 也都需要多一根做比较
        return Math.max(Math.max(plan.getRsiPeriod() + 1, kdjNeed), macdNeed) + 1;
    }

    /**
     * 挑出收盘价为正的日线，零或负值说明数据异常
     *
     * @param bars 原始日线
     * @return 可用日线
     */
    private List<MarketBar> usable(List<MarketBar> bars) {
        List<MarketBar> usable = new ArrayList<>();
        if (bars == null) {
            return usable;
        }
        for (MarketBar bar : bars) {
            if (bar.getDate() != null && bar.getClose() != null && bar.getClose().signum() > 0) {
                usable.add(bar);
            }
        }
        return usable;
    }

    /**
     * 按 Wilder 平滑算 RSI，全段无下跌按 100 处理避免除零
     *
     * @param result 输出
     * @param bars   可用日线
     * @param plan   指标参数
     */
    private void computeRsi(IndicatorResult result, List<MarketBar> bars, IndicatorPlan plan) {
        int period = plan.getRsiPeriod();
        List<BigDecimal> changes = new ArrayList<>();
        for (int i = 1; i < bars.size(); i++) {
            changes.add(bars.get(i).getClose().subtract(bars.get(i - 1).getClose()));
        }
        if (changes.size() < period) {
            return;
        }
        BigDecimal gainSum = ZERO;
        BigDecimal lossSum = ZERO;
        for (int i = 0; i < period; i++) {
            BigDecimal diff = changes.get(i);
            if (diff.signum() >= 0) {
                gainSum = gainSum.add(diff);
            } else {
                lossSum = lossSum.subtract(diff);
            }
        }
        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), CONTEXT);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), CONTEXT);
        for (int i = period; i < changes.size(); i++) {
            BigDecimal diff = changes.get(i);
            BigDecimal up = diff.signum() > 0 ? diff : ZERO;
            BigDecimal down = diff.signum() < 0 ? diff.negate() : ZERO;
            // Wilder 平滑：新均值 = (旧均值×(n−1) + 本期) / n
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1L)).add(up)
                    .divide(BigDecimal.valueOf(period), CONTEXT);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1L)).add(down)
                    .divide(BigDecimal.valueOf(period), CONTEXT);
        }
        if (avgLoss.signum() == 0) {
            result.setRsi(BigDecimal.valueOf(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP));
            return;
        }
        BigDecimal rs = avgGain.divide(avgLoss, CONTEXT);
        result.setRsi(BigDecimal.valueOf(HUNDRED)
                .subtract(BigDecimal.valueOf(HUNDRED).divide(BigDecimal.ONE.add(rs), CONTEXT))
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 算 KDJ，RSV 用最高最低价，K 与 D 按设定系数递推，J 取 3K−2D
     *
     * @param result 输出
     * @param bars   可用日线
     * @param plan   指标参数
     */
    private void computeKdj(IndicatorResult result, List<MarketBar> bars, IndicatorPlan plan) {
        int period = plan.getKdjPeriod();
        if (bars.size() < period || !hasRanges(bars)) {
            if (!hasRanges(bars)) {
                result.getRemarks().add("日线缺最高价或最低价，KDJ 无法计算；请重新取一次日线");
            }
            return;
        }
        BigDecimal k = BigDecimal.valueOf(50);
        BigDecimal d = BigDecimal.valueOf(50);
        BigDecimal kSmooth = BigDecimal.valueOf(plan.getKdjKSmooth());
        BigDecimal dSmooth = BigDecimal.valueOf(plan.getKdjDSmooth());
        for (int i = period - 1; i < bars.size(); i++) {
            List<MarketBar> window = bars.subList(i - period + 1, i + 1);
            BigDecimal highest = null;
            BigDecimal lowest = null;
            for (MarketBar bar : window) {
                if (bar.getHigh() == null || bar.getLow() == null) {
                    highest = null;
                    break;
                }
                highest = highest == null || bar.getHigh().compareTo(highest) > 0 ? bar.getHigh() : highest;
                lowest = lowest == null || bar.getLow().compareTo(lowest) < 0 ? bar.getLow() : lowest;
            }
            if (highest == null || lowest == null) {
                continue;
            }
            BigDecimal range = highest.subtract(lowest);
            // 区间为 0 说明窗口内价格完全不动，RSV 取 50 中位，避免除零把指标打成 100
            BigDecimal rsv = range.signum() == 0 ? BigDecimal.valueOf(50)
                    : bars.get(i).getClose().subtract(lowest).multiply(HUNDRED_DECIMAL)
                            .divide(range, CONTEXT);
            k = k.multiply(kSmooth.subtract(BigDecimal.ONE)).add(rsv).divide(kSmooth, CONTEXT);
            d = d.multiply(dSmooth.subtract(BigDecimal.ONE)).add(k).divide(dSmooth, CONTEXT);
        }
        result.setK(k.setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setD(d.setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setJ(k.multiply(BigDecimal.valueOf(3)).subtract(d.multiply(BigDecimal.valueOf(2)))
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 算 MACD，EMA 以首值为种子，DIF 为快慢线之差，DEA 为 DIF 的 EMA
     *
     * @param result 输出
     * @param bars   可用日线
     * @param plan   指标参数
     */
    private void computeMacd(IndicatorResult result, List<MarketBar> bars, IndicatorPlan plan) {
        // EMA 种子需要 slow 根才收敛，再留一根做前后比较，样本不够就不给 MACD
        if (bars.size() < plan.getMacdSlow() + 1) {
            return;
        }
        List<BigDecimal> closes = new ArrayList<>(bars.size());
        bars.forEach(bar -> closes.add(bar.getClose()));
        List<BigDecimal> fast = ema(closes, plan.getMacdFast());
        List<BigDecimal> slow = ema(closes, plan.getMacdSlow());
        List<BigDecimal> dif = new ArrayList<>(closes.size());
        for (int i = 0; i < closes.size(); i++) {
            dif.add(fast.get(i).subtract(slow.get(i)));
        }
        List<BigDecimal> dea = ema(dif, plan.getMacdSignal());
        int last = closes.size() - 1;
        result.setDif(dif.get(last).setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setDea(dea.get(last).setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setMacdBar(dif.get(last).subtract(dea.get(last)).multiply(BigDecimal.valueOf(2))
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setPreviousDif(dif.get(last - 1).setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.setPreviousDea(dea.get(last - 1).setScale(RESULT_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 按均线值递推算指数移动平均，alpha = 2 / (n + 1)，首值作种子
     *
     * @param values 输入序列
     * @param period 周期
     * @return 与输入等长的 EMA 序列
     */
    private List<BigDecimal> ema(List<BigDecimal> values, int period) {
        List<BigDecimal> result = new ArrayList<>(values.size());
        BigDecimal alpha = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), CONTEXT);
        for (BigDecimal value : values) {
            BigDecimal previous = result.isEmpty() ? value : result.get(result.size() - 1);
            result.add(previous.add(alpha.multiply(value.subtract(previous))));
        }
        return result;
    }

    /**
     * 是否每根日线都有最高最低价，KDJ 的 RSV 必须用到它们
     *
     * @param bars 日线
     * @return 齐全返回 true
     */
    private boolean hasRanges(List<MarketBar> bars) {
        for (MarketBar bar : bars) {
            if (!bar.hasRange()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按阈值与穿越判定信号，并在说明里写清触发所依据的数值
     *
     * @param result 输出
     * @param plan   指标参数
     */
    private void fillSignals(IndicatorResult result, IndicatorPlan plan) {
        if (result.getRsi() != null && plan.getRsiUpper() != null
                && result.getRsi().compareTo(plan.getRsiUpper()) >= 0) {
            result.getSignals().add(IndicatorSignal.RSI_OVERBOUGHT);
            result.getRemarks().add("RSI " + plain(result.getRsi()) + " 不低于超买阈值 "
                    + plain(plan.getRsiUpper()));
        }
        if (result.getRsi() != null && plan.getRsiLower() != null
                && result.getRsi().compareTo(plan.getRsiLower()) <= 0) {
            result.getSignals().add(IndicatorSignal.RSI_OVERSOLD);
            result.getRemarks().add("RSI " + plain(result.getRsi()) + " 不高于超卖阈值 "
                    + plain(plan.getRsiLower()));
        }
        fillMacdSignal(result);
        fillKdjSignal(result);
    }

    /**
     * MACD 金叉死叉：上一根 DIF 在 DEA 另一侧、本根越过来
     *
     * @param result 输出
     */
    private void fillMacdSignal(IndicatorResult result) {
        if (result.getDif() == null || result.getDea() == null
                || result.getPreviousDif() == null || result.getPreviousDea() == null) {
            return;
        }
        BigDecimal previousGap = result.getPreviousDif().subtract(result.getPreviousDea());
        BigDecimal currentGap = result.getDif().subtract(result.getDea());
        if (previousGap.signum() <= 0 && currentGap.signum() > 0) {
            result.getSignals().add(IndicatorSignal.MACD_GOLDEN_CROSS);
            result.getRemarks().add("MACD 金叉：DIF 由上穿 DEA，前一根差值 " + plain(previousGap) + "，本根 "
                    + plain(currentGap));
        } else if (previousGap.signum() >= 0 && currentGap.signum() < 0) {
            result.getSignals().add(IndicatorSignal.MACD_DEAD_CROSS);
            result.getRemarks().add("MACD 死叉：DIF 由下穿 DEA，前一根差值 " + plain(previousGap) + "，本根 "
                    + plain(currentGap));
        }
    }

    /**
     * KDJ 金叉死叉：K 上穿 D 且在低位算金叉，下穿 D 且在高位算死叉
     *
     * @param result 输出
     */
    private void fillKdjSignal(IndicatorResult result) {
        if (result.getK() == null || result.getD() == null) {
            return;
        }
        BigDecimal gap = result.getK().subtract(result.getD());
        // 用 K 与 D 自身水平过滤掉震荡区里的无效穿越，避免噪声信号进邮件
        boolean lowZone = result.getK().compareTo(BigDecimal.valueOf(30)) < 0
                || result.getD().compareTo(BigDecimal.valueOf(30)) < 0;
        boolean highZone = result.getK().compareTo(BigDecimal.valueOf(70)) > 0
                || result.getD().compareTo(BigDecimal.valueOf(70)) > 0;
        if (gap.signum() > 0 && lowZone) {
            result.getSignals().add(IndicatorSignal.KDJ_GOLDEN_CROSS);
            result.getRemarks().add("KDJ 金叉：K " + plain(result.getK()) + " 高于 D " + plain(result.getD())
                    + " 且处于低位");
        } else if (gap.signum() < 0 && highZone) {
            result.getSignals().add(IndicatorSignal.KDJ_DEAD_CROSS);
            result.getRemarks().add("KDJ 死叉：K " + plain(result.getK()) + " 低于 D " + plain(result.getD())
                    + " 且处于高位");
        }
    }

    /**
     * 标记为无法计算并写入原因
     *
     * @param result 输出
     * @param reason 原因
     * @return 同一结果
     */
    private IndicatorResult unavailable(IndicatorResult result, String reason) {
        result.setUnavailableReason(reason);
        result.getRemarks().add(reason);
        return result;
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
