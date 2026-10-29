package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.enums.GridAction;
import com.lilac.strategy.model.GridDecision;
import com.lilac.strategy.model.GridPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 网格规则计算器，纯计算不访问数据库，负责划格、定位当前格子与判定穿越
 */
@Slf4j
@Component
public class GridCalculator {

    private static final int PRICE_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;
    private static final int MIN_GRIDS = 2;

    /**
     * 按当前价与参考价判定是否越过网格线，并给出买卖方向与金额
     *
     * @param plan          网格参数，含问题清单
     * @param currentPrice  当前价，单位元
     * @param referencePrice 参考价（一般为上一交易日收盘或最近一次观察价），用于判断穿越方向
     * @param dividendAction 同标的的红利动作，用于互斥判定，可为 null
     * @return 判定结果，任何信息不全的场景都返回未触发并说明原因
     */
    public GridDecision decide(GridPlan plan, BigDecimal currentPrice, BigDecimal referencePrice,
                               DividendAction dividendAction) {
        GridDecision decision = new GridDecision();
        decision.setCurrentPrice(currentPrice);
        if (plan == null || !plan.complete()) {
            if (plan != null && plan.getIssues() != null) {
                decision.getRemarks().addAll(plan.getIssues());
            }
            decision.setAction(GridAction.NONE);
            decision.getRemarks().add("网格参数缺失或不自洽，暂不计算网格");
            return decision;
        }
        List<BigDecimal> lines = lines(plan);
        decision.setLineCount(lines.size());
        int grids = gridCount(plan);
        if (grids < MIN_GRIDS) {
            decision.setAction(GridAction.NONE);
            decision.getRemarks().add("按当前上下界与格子大小只能划出 " + grids + " 格，至少需要 " + MIN_GRIDS + " 格");
            return decision;
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            decision.setAction(GridAction.NONE);
            decision.getRemarks().add("当前价取不到，无法定位网格");
            return decision;
        }
        BigDecimal price = currentPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        decision.setCurrentGrid(gridIndexOf(plan, price));
        if (dividendAction == DividendAction.ADD || dividendAction == DividendAction.REDUCE) {
            // 同一标的不给冲突建议：红利已经给出加减仓方向时，网格不得给出反方向动作
            decision.setAction(GridAction.SUPPRESSED);
            decision.setSuppressed(Boolean.TRUE);
            decision.getRemarks().add("同标的有红利" + dividendAction.getLabel() + "建议，网格提醒本次让位，不重复给出反向动作");
            return decision;
        }
        decision.setSuppressed(Boolean.FALSE);
        if (referencePrice == null || referencePrice.signum() <= 0) {
            decision.setAction(GridAction.NONE);
            decision.getRemarks().add("缺参考价（上一交易日收盘价），无法判断是否穿越网格线");
            return decision;
        }
        BigDecimal reference = referencePrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> crossed = crossedLines(lines, reference, price);
        if (crossed.isEmpty()) {
            decision.setAction(GridAction.NONE);
            decision.getRemarks().add("当前价 " + plain(price) + " 与参考价 " + plain(reference)
                    + " 之间没有网格线被穿越，本次不触发");
            return decision;
        }
        boolean down = price.compareTo(reference) < 0;
        BigDecimal nearest = down ? crossed.get(crossed.size() - 1) : crossed.get(0);
        BigDecimal amount = plan.getAmountPerGrid()
                .multiply(BigDecimal.valueOf(crossed.size()))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        decision.setAction(down ? GridAction.BUY : GridAction.SELL);
        decision.setCrossedLine(nearest);
        decision.setDistanceToLine(price.subtract(nearest).setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        decision.setSuggestAmount(amount);
        decision.getRemarks().add((down ? "下穿" : "上穿") + "网格线 " + plain(nearest) + "，参考价 "
                + plain(reference) + " → 当前价 " + plain(price));
        decision.getRemarks().add("共越过 " + crossed.size() + " 条网格线，每格 "
                + plain(plan.getAmountPerGrid()) + " 元，建议" + (down ? "买入 " : "卖出 ") + plain(amount) + " 元");
        if (crossed.size() > 1) {
            decision.getRemarks().add("单日跨越多格时按格数累加，不拆成多次独立提醒");
        }
        appendRangeNote(decision, plan, price);
        return decision;
    }

    /**
     * 按上下界与格子大小算能划出多少格，与保存校验同一口径
     *
     * @param plan 网格参数
     * @return 格数，参数不全时为 0
     */
    public int gridCount(GridPlan plan) {
        if (plan == null || plan.getLowerBound() == null || plan.getUpperBound() == null
                || plan.getStep() == null || plan.getStep().signum() <= 0) {
            return 0;
        }
        if (plan.getUpperBound().compareTo(plan.getLowerBound()) <= 0) {
            return 0;
        }
        return plan.getUpperBound().subtract(plan.getLowerBound())
                .divide(plan.getStep(), 0, RoundingMode.DOWN).intValue();
    }

    /**
     * 按上下界与格子大小列出网格线，含下界与不超过上界的最外一条
     *
     * @param plan 网格参数
     * @return 网格线价格，按升序
     */
    public List<BigDecimal> lines(GridPlan plan) {
        List<BigDecimal> lines = new ArrayList<>();
        if (plan == null || plan.getLowerBound() == null || plan.getUpperBound() == null
                || plan.getStep() == null || plan.getStep().signum() <= 0) {
            return lines;
        }
        BigDecimal lower = plan.getLowerBound();
        BigDecimal upper = plan.getUpperBound();
        BigDecimal step = plan.getStep();
        if (lower.compareTo(upper) >= 0) {
            return lines;
        }
        int count = upper.subtract(lower).divide(step, 0, RoundingMode.DOWN).intValue();
        // 上界本身也画一条，否则价格摸到上界时没有可卖的线
        for (int index = 0; index <= count; index++) {
            lines.add(lower.add(step.multiply(BigDecimal.valueOf(index)))
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        }
        if (!lines.get(lines.size() - 1).equals(upper.setScale(PRICE_SCALE, RoundingMode.HALF_UP))) {
            lines.add(upper.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        }
        return lines;
    }

    /**
     * 定位当前价落在第几格，从下界那格起算为 0；超出上下界时返回 null
     *
     * @param plan  网格参数
     * @param price 当前价
     * @return 格子序号，超出区间返回 null
     */
    private Integer gridIndexOf(GridPlan plan, BigDecimal price) {
        if (price.compareTo(plan.getLowerBound()) < 0 || price.compareTo(plan.getUpperBound()) > 0) {
            return null;
        }
        return price.subtract(plan.getLowerBound())
                .divide(plan.getStep(), 0, RoundingMode.FLOOR)
                .intValue();
    }

    /**
     * 找出参考价与当前价之间被穿越的网格线，参考价压在线上时那条不算穿越
     *
     * @param lines     网格线
     * @param reference 参考价
     * @param price     当前价
     * @return 被穿越的网格线，按升序
     */
    private List<BigDecimal> crossedLines(List<BigDecimal> lines, BigDecimal reference, BigDecimal price) {
        boolean down = price.compareTo(reference) < 0;
        List<BigDecimal> crossed = new ArrayList<>();
        for (BigDecimal line : lines) {
            // 下穿要求线严格低于参考价且不低于当前价；上穿方向相反，参考价压在线上时不算
            boolean hit = down
                    ? line.compareTo(reference) < 0 && line.compareTo(price) >= 0
                    : line.compareTo(reference) > 0 && line.compareTo(price) <= 0;
            if (hit) {
                crossed.add(line);
            }
        }
        return crossed;
    }

    /**
     * 价格跑到区间外时明确说明，避免使用者以为网格还在工作
     *
     * @param decision 输出
     * @param plan     网格参数
     * @param price    当前价
     */
    private void appendRangeNote(GridDecision decision, GridPlan plan, BigDecimal price) {
        if (price.compareTo(plan.getUpperBound()) > 0) {
            decision.getRemarks().add("当前价已高于网格上界 " + plain(plan.getUpperBound())
                    + "，超出后不再有可卖的网格线，需重新划格");
        } else if (price.compareTo(plan.getLowerBound()) < 0) {
            decision.getRemarks().add("当前价已低于网格下界 " + plain(plan.getLowerBound())
                    + "，超出后不再有可买的网格线，需重新划格");
        }
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
