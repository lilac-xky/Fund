package com.lilac.strategy;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.enums.BondTrendSignal;
import com.lilac.strategy.model.BondPlan;
import com.lilac.strategy.model.BondTrendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 债券趋势计算器，纯计算不访问数据库，负责横盘天数、回归斜率与波动率的判定
 */
@Slf4j
@Component
public class BondTrendCalculator {

    private static final int HUNDRED = 100;
    private static final int RESULT_SCALE = 4;
    private static final int SLOPE_SCALE = 6;
    private static final int MIN_SAMPLES = 5;
    private static final BigDecimal TRADING_DAYS = BigDecimal.valueOf(250);
    private static final MathContext SLOPE_CONTEXT = MathContext.DECIMAL64;

    /**
     * 按净值序列判定横盘或趋势转弱，数据不足时明确说明而不给结论
     *
     * @param points 净值序列，按日期升序
     * @param plan   监控参数
     * @return 趋势判定结果
     */
    public BondTrendResult evaluate(List<FundNetWorthPoint> points, BondPlan plan) {
        BondTrendResult result = new BondTrendResult();
        if (plan == null || !plan.complete()) {
            if (plan != null && plan.getIssues() != null) {
                result.getRemarks().addAll(plan.getIssues());
            }
            return unavailable(result, "债券监控参数缺失或不自洽，暂不判断趋势");
        }
        List<FundNetWorthPoint> usable = usable(points);
        if (usable.size() < MIN_SAMPLES) {
            return unavailable(result, "可用净值仅 " + usable.size() + " 个交易日，至少要 " + MIN_SAMPLES
                    + " 个才能判断横盘与趋势");
        }
        int window = Math.min(plan.getWindow(), usable.size());
        List<FundNetWorthPoint> windowPoints = usable.subList(usable.size() - window, usable.size());
        fillWindow(result, windowPoints, plan);
        fillSideways(result, usable, plan);
        fillSignal(result, plan);
        return result;
    }

    /**
     * 挑出净值为正的点，零或负值说明数据异常，参与回归会得出错误斜率
     *
     * @param points 原始净值序列
     * @return 可用序列
     */
    private List<FundNetWorthPoint> usable(List<FundNetWorthPoint> points) {
        List<FundNetWorthPoint> usable = new ArrayList<>();
        if (points == null) {
            return usable;
        }
        for (FundNetWorthPoint point : points) {
            if (point.getNetWorthDate() != null && point.getUnitNetWorth() != null
                    && point.getUnitNetWorth().signum() > 0) {
                usable.add(point);
            }
        }
        return usable;
    }

    /**
     * 填回看窗口的涨幅、斜率与波动率
     *
     * @param result 输出
     * @param window 窗口内的净值序列
     * @param plan   监控参数
     */
    private void fillWindow(BondTrendResult result, List<FundNetWorthPoint> window, BondPlan plan) {
        result.setWindow(window.size());
        result.setWindowStart(window.get(0).getNetWorthDate());
        result.setWindowEnd(window.get(window.size() - 1).getNetWorthDate());
        result.setSamples(window.size());
        result.setWindowReturn(returnOf(window));
        List<BigDecimal> returns = dailyReturns(window);
        result.setVolatility(volatilityOf(returns));
        // 斜率对净值取对数后回归，得到的才是「趋势」；直接回归日收益会在稳定上涨时得出负斜率
        BigDecimal slope = logSlopeOf(window);
        result.setSlope(slope);
        result.setAnnualizedSlope(slope == null ? null
                : slope.multiply(TRADING_DAYS).multiply(BigDecimal.valueOf(HUNDRED))
                        .setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        result.getRemarks().add("回看 " + window.size() + " 个交易日（" + result.getWindowStart() + " 至 "
                + result.getWindowEnd() + "），累计涨幅 " + percent(result.getWindowReturn())
                + "，年化斜率 " + percent(result.getAnnualizedSlope()) + "，日波动率 "
                + percent(result.getVolatility()));
    }

    /**
     * 从最新一天往回数连续横盘的天数，单日涨跌不超过带宽容差即算横盘
     *
     * @param result 输出
     * @param usable 全部可用净值序列
     * @param plan   监控参数
     */
    private void fillSideways(BondTrendResult result, List<FundNetWorthPoint> usable, BondPlan plan) {
        BigDecimal band = plan.getSidewaysBand();
        int days = 0;
        int tail = usable.size() - 1;
        for (int i = tail; i > 0; i--) {
            BigDecimal change = dailyChange(usable.get(i - 1), usable.get(i));
            if (change == null || change.abs().compareTo(band) > 0) {
                break;
            }
            days++;
        }
        result.setSidewaysDays(days);
        if (days > 0) {
            result.setSidewaysEnd(usable.get(tail).getNetWorthDate());
            result.setSidewaysStart(usable.get(tail - days).getNetWorthDate());
            result.setSidewaysReturn(returnOf(usable.subList(tail - days, tail + 1)));
        }
    }

    /**
     * 综合横盘天数与斜率给出信号，两者都达到才算转弱，避免单日波动误报
     *
     * @param result 输出
     * @param plan   监控参数
     */
    private void fillSignal(BondTrendResult result, BondPlan plan) {
        boolean sideways = result.getSidewaysDays() != null && plan.getSidewaysDays() != null
                && result.getSidewaysDays() >= plan.getSidewaysDays()
                && belowReturn(result.getSidewaysReturn(), plan.getSidewaysReturn());
        boolean weak = isWeak(result, plan);
        if (sideways) {
            result.setSignal(BondTrendSignal.SIDEWAYS);
            result.getRemarks().add("已连续横盘 " + result.getSidewaysDays() + " 个交易日（" + result.getSidewaysStart()
                    + " 至 " + result.getSidewaysEnd() + "），区间累计涨幅 " + percent(result.getSidewaysReturn())
                    + "，达到 " + plan.getSidewaysDays() + " 日且累计涨幅低于 " + percent(plan.getSidewaysReturn())
                    + " 的阈值，可考虑换仓");
            return;
        }
        if (weak) {
            result.setSignal(BondTrendSignal.WEAK);
            result.getRemarks().add("年化斜率 " + percent(result.getAnnualizedSlope()) + " 低于转弱阈值 "
                    + percent(plan.getWeakSlope()) + "，日波动率 " + percent(result.getVolatility())
                    + " 达到 " + percent(plan.getWeakVolatility()) + "，趋势已转弱，可考虑换仓");
            return;
        }
        result.setSignal(BondTrendSignal.NORMAL);
        result.getRemarks().add(notAlertingNote(result, plan, sideways, weak));
    }

    /**
     * 判断趋势是否转弱，斜率为负且跌破阈值、波动率同时达到下限
     *
     * @param result 输出
     * @param plan   监控参数
     * @return 转弱返回 true
     */
    private boolean isWeak(BondTrendResult result, BondPlan plan) {
        if (result.getAnnualizedSlope() == null || result.getVolatility() == null) {
            return false;
        }
        boolean slopeWeak = result.getAnnualizedSlope().compareTo(plan.getWeakSlope()) < 0;
        boolean volatileEnough = result.getVolatility().compareTo(plan.getWeakVolatility()) >= 0;
        return slopeWeak && volatileEnough;
    }

    /**
     * 未触发时说明差在哪，使用者才知道离阈值还有多远
     *
     * @param result    输出
     * @param plan      监控参数
     * @param sideways  是否已达横盘
     * @param weak      是否已达转弱
     * @return 说明文字
     */
    private String notAlertingNote(BondTrendResult result, BondPlan plan, boolean sideways, boolean weak) {
        if (result.getSidewaysDays() == null) {
            return "未达换仓提醒条件：横盘天数与斜率均在阈值内";
        }
        String dayText = "横盘 " + result.getSidewaysDays() + " 日 / 阈值 " + plan.getSidewaysDays() + " 日";
        String slopeText = "年化斜率 " + percent(result.getAnnualizedSlope()) + " / 转弱阈值 "
                + percent(plan.getWeakSlope());
        if (sideways && !weak) {
            return "未达换仓提醒条件：" + dayText + " 已满足，但累计涨幅 " + percent(result.getSidewaysReturn())
                    + " 未低于 " + percent(plan.getSidewaysReturn());
        }
        return "未达换仓提醒条件：" + dayText + "，" + slopeText;
    }

    /**
     * 判断累计涨幅是否低于给定上限
     *
     * @param value 累计涨幅，百分点
     * @param limit 上限，百分点
     * @return 低于返回 true，任一为空按不满足处理
     */
    private boolean belowReturn(BigDecimal value, BigDecimal limit) {
        return value != null && limit != null && value.compareTo(limit) < 0;
    }

    /**
     * 相邻两点的单日涨跌，百分点
     *
     * @param previous 前一日
     * @param current  当日
     * @return 涨跌幅，无法计算返回 null
     */
    private BigDecimal dailyChange(FundNetWorthPoint previous, FundNetWorthPoint current) {
        BigDecimal before = previous.getUnitNetWorth();
        BigDecimal after = current.getUnitNetWorth();
        if (before == null || after == null || before.signum() == 0) {
            return null;
        }
        return after.subtract(before).multiply(BigDecimal.valueOf(HUNDRED))
                .divide(before, RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 取序列首尾的单位净值累计涨幅，百分点
     *
     * @param points 净值序列
     * @return 累计涨幅
     */
    private BigDecimal returnOf(List<FundNetWorthPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        BigDecimal first = points.get(0).getUnitNetWorth();
        BigDecimal last = points.get(points.size() - 1).getUnitNetWorth();
        if (first == null || last == null || first.signum() == 0) {
            return null;
        }
        return last.subtract(first).multiply(BigDecimal.valueOf(HUNDRED))
                .divide(first, RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 逐日涨跌序列，百分点
     *
     * @param points 净值序列
     * @return 涨跌序列
     */
    private List<BigDecimal> dailyReturns(List<FundNetWorthPoint> points) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            BigDecimal change = dailyChange(points.get(i - 1), points.get(i));
            if (change != null) {
                returns.add(change);
            }
        }
        return returns;
    }

    /**
     * 对净值取自然对数后对时间序号做最小二乘回归，斜率即每日对数收益，单位无量纲
     *
     * @param points 净值序列
     * @return 斜率，样本不足返回 null
     */
    private BigDecimal logSlopeOf(List<FundNetWorthPoint> points) {
        int size = points.size();
        if (size < MIN_SAMPLES) {
            return null;
        }
        BigDecimal meanX = BigDecimal.valueOf(size - 1L).divide(BigDecimal.valueOf(2), SLOPE_CONTEXT);
        BigDecimal meanY = BigDecimal.ZERO;
        for (FundNetWorthPoint point : points) {
            meanY = meanY.add(ln(point.getUnitNetWorth()));
        }
        meanY = meanY.divide(BigDecimal.valueOf(size), SLOPE_CONTEXT);
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (int i = 0; i < size; i++) {
            BigDecimal dx = BigDecimal.valueOf(i).subtract(meanX);
            numerator = numerator.add(dx.multiply(ln(points.get(i).getUnitNetWorth()).subtract(meanY)));
            denominator = denominator.add(dx.multiply(dx));
        }
        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SLOPE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 取自然对数，BigDecimal 没有内置对数，借 double 计算后回填
     *
     * @param value 正数
     * @return 自然对数
     */
    private BigDecimal ln(BigDecimal value) {
        return BigDecimal.valueOf(Math.log(value.doubleValue()));
    }

    /**
     * 日收益的样本标准差，百分点
     *
     * @param returns 日涨跌序列
     * @return 标准差，样本不足返回 null
     */
    private BigDecimal volatilityOf(List<BigDecimal> returns) {
        int size = returns.size();
        if (size < 2) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : returns) {
            sum = sum.add(value);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(size), SLOPE_CONTEXT);
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal value : returns) {
            BigDecimal diff = value.subtract(mean);
            squares = squares.add(diff.multiply(diff));
        }
        // 用样本方差（n−1），样本量小时无偏性更稳
        BigDecimal variance = squares.divide(BigDecimal.valueOf(size - 1L), SLOPE_CONTEXT);
        return variance.sqrt(SLOPE_CONTEXT).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 标记数据不足并写入原因
     *
     * @param result 输出
     * @param reason 原因
     * @return 同一结果
     */
    private BondTrendResult unavailable(BondTrendResult result, String reason) {
        result.setSignal(BondTrendSignal.UNAVAILABLE);
        result.setUnavailableReason(reason);
        result.getRemarks().add(reason);
        return result;
    }

    /**
     * 计算横盘天数的自然日跨度，供文案说明
     *
     * @param start 起点
     * @param end   终点
     * @return 自然日数，任一为空返回 0
     */
    public long naturalDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 百分比格式化，带符号并保留两位，空值给破折号
     *
     * @param value 数值
     * @return 文本
     */
    private String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString() + "%";
    }
}
