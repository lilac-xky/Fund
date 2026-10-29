package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 现金分红推算器，用累计净值与单位净值的差算出建仓以来已分掉的部分
 */
@Component
public class DividendCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int LOOKBACK_DAYS = 10;

    /**
     * 推算建仓以来的现金分红金额，数据不足或算不出正数时返回 null
     *
     * @param shares   合计持有份额
     * @param since    建仓日期
     * @param latest   最新净值点，需带单位净值与累计净值
     * @param history  建仓日附近的净值序列，按日期升序
     * @return 分红金额，无法推算返回 null
     */
    public Result calculate(BigDecimal shares, LocalDate since, FundNetWorthPoint latest,
                            List<FundNetWorthPoint> history) {
        if (shares == null || shares.signum() <= 0 || since == null || latest == null) {
            return null;
        }
        if (latest.getAccumulatedNetWorth() == null || latest.getUnitNetWorth() == null) {
            return null;
        }
        FundNetWorthPoint start = startPoint(since, history);
        if (start == null || start.getAccumulatedNetWorth() == null || start.getUnitNetWorth() == null) {
            return null;
        }
        // 累计净值涨得比单位净值多的部分，就是这期间以现金形式分出去的
        BigDecimal accumulatedGain = latest.getAccumulatedNetWorth().subtract(start.getAccumulatedNetWorth());
        BigDecimal priceGain = latest.getUnitNetWorth().subtract(start.getUnitNetWorth());
        BigDecimal perShare = accumulatedGain.subtract(priceGain);
        if (perShare.signum() <= 0) {
            return new Result(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    "自 " + since + " 建仓以来无现金分红");
        }
        BigDecimal amount = shares.multiply(perShare).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Result(amount, "自 " + since + " 建仓以来按累计净值推算的现金分红，不含在浮盈亏内");
    }

    /**
     * 取建仓日当天或之前最近一期的净值点，建仓日无净值时往前找
     *
     * @param since   建仓日期
     * @param history 建仓日附近的净值序列
     * @return 起点净值点，取不到返回 null
     */
    private FundNetWorthPoint startPoint(LocalDate since, List<FundNetWorthPoint> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        FundNetWorthPoint matched = null;
        for (FundNetWorthPoint point : history) {
            if (point.getNetWorthDate() == null || point.getAccumulatedNetWorth() == null) {
                continue;
            }
            if (!point.getNetWorthDate().isAfter(since)) {
                matched = point;
            }
        }
        return matched;
    }

    /**
     * 查询建仓日附近净值的回溯天数
     *
     * @return 天数
     */
    public int lookbackDays() {
        return LOOKBACK_DAYS;
    }

    /**
     * 分红推算结果，金额与口径说明成对给出
     */
    public record Result(BigDecimal amount, String note) {
    }
}
