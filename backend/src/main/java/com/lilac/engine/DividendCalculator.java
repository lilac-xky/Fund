package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.enums.DividendMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 现金分红推算器，逐日识别除息日并把每笔分红拆成「日期 + 每份金额」
 */
@Component
public class DividendCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int PRICE_SCALE = 6;
    private static final int LOOKBACK_DAYS = 10;
    private static final BigDecimal MIN_DISTRIBUTION = new BigDecimal("0.0002");
    /** 单次分红不得超过前一日单位净值的这个比例，超出说明是净值跳日而不是分红 */
    private static final BigDecimal MAX_PAYOUT_RATIO = new BigDecimal("0.2");

    /**
     * 推算建仓以来的现金分红，数据不足时返回 null
     *
     * @param shares   合计持有份额
     * @param since    建仓日期
     * @param latest   最新净值点，需带单位净值
     * @param history  建仓日附近的净值序列，按日期升序
     * @param method   分红方式，红利再投时不计现金
     * @return 分红结果，无法推算返回 null
     */
    public Result calculate(BigDecimal shares, LocalDate since, FundNetWorthPoint latest,
                            List<FundNetWorthPoint> history, DividendMethod method) {
        if (shares == null || shares.signum() <= 0 || since == null || latest == null) {
            return null;
        }
        if (latest.getUnitNetWorth() == null) {
            return null;
        }
        if (history == null || history.size() < 2) {
            // 没有前后两期净值就识别不出除息，宁可不显示也不显示 0
            return null;
        }
        List<Distribution> distributions = distributions(since, history);
        if (distributions.isEmpty()) {
            return new Result(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    "自 " + since + " 建仓以来无除息记录", List.of(), DividendMethod.CASH);
        }
        if (method == DividendMethod.REINVEST) {
            // 红利再投的分红已经折成份额，再算一遍现金会与份额口径重复
            return new Result(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    "分红方式为红利再投，分红已折成份额，不计入现金", distributions, DividendMethod.REINVEST);
        }
        BigDecimal perShare = distributions.stream().map(Distribution::perShare)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = shares.multiply(perShare).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Result(amount, "自 " + since + " 建仓以来共 " + distributions.size()
                + " 次除息，每份合计 " + plain(perShare) + " 元，不含在浮盈亏内", distributions,
                DividendMethod.CASH);
    }

    /**
     * 逐日识别除息：优先用累计净值与单位净值的差，缺累计净值时退回按当日回报反推
     *
     * @param since   建仓日期
     * @param history 净值序列，按日期升序
     * @return 除息记录，按日期升序
     */
    private List<Distribution> distributions(LocalDate since, List<FundNetWorthPoint> history) {
        List<Distribution> result = new ArrayList<>();
        if (history == null || history.size() < 2) {
            return result;
        }
        for (int i = 1; i < history.size(); i++) {
            FundNetWorthPoint previous = history.get(i - 1);
            FundNetWorthPoint current = history.get(i);
            // 除息日当天买入的份额不享受该次分红，故要求除息日严格晚于建仓日
            if (current.getNetWorthDate() == null || !current.getNetWorthDate().isAfter(since)) {
                continue;
            }
            if (previous.getUnitNetWorth() == null || current.getUnitNetWorth() == null) {
                continue;
            }
            BigDecimal perShare = perShareOf(previous, current);
            // 缺交易日会让「本应到的净值」误判成巨额分红，超出常理的一律按数据缺口忽略
            if (perShare == null || perShare.signum() <= 0
                    || perShare.compareTo(previous.getUnitNetWorth().multiply(MAX_PAYOUT_RATIO)) > 0) {
                continue;
            }
            if (perShare.compareTo(MIN_DISTRIBUTION) >= 0) {
                result.add(new Distribution(current.getNetWorthDate(),
                        perShare.setScale(PRICE_SCALE, RoundingMode.HALF_UP)));
            }
        }
        return result;
    }

    /**
     * 算单个除息日的每份分红，取不到返回 null
     *
     * @param previous 前一个净值点
     * @param current  当日净值点
     * @return 每份分红金额，无分红返回 null
     */
    private BigDecimal perShareOf(FundNetWorthPoint previous, FundNetWorthPoint current) {
        if (previous.getAccumulatedNetWorth() != null && current.getAccumulatedNetWorth() != null) {
            // 累计净值不会被分红打掉，它的涨幅与单位净值涨幅之差不依赖当日回报，也不受跳日影响
            return current.getAccumulatedNetWorth().subtract(current.getUnitNetWorth())
                    .subtract(previous.getAccumulatedNetWorth().subtract(previous.getUnitNetWorth()));
        }
        if (current.getEquityReturn() == null) {
            return null;
        }
        // 缺累计净值时用当日回报正推出「本应到的单位净值」，与实际值的差就是分掉的部分
        BigDecimal expected = previous.getUnitNetWorth().multiply(
                BigDecimal.ONE.add(current.getEquityReturn()
                        .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)));
        return expected.subtract(current.getUnitNetWorth());
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

    /**
     * 一次除息，日期与每份金额成对给出
     */
    public record Distribution(LocalDate date, BigDecimal perShare) {
    }

    /**
     * 分红推算结果，金额、口径说明与逐笔明细一起给出
     */
    public record Result(BigDecimal amount, String note, List<Distribution> distributions, DividendMethod method) {
    }
}
