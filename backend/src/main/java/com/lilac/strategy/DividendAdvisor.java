package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.strategy.model.DividendDecision;
import com.lilac.strategy.model.DividendPlan;
import com.lilac.strategy.model.DividendSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 红利加减仓规则计算器，纯计算不访问数据库，金额一律向下取整到元
 */
@Slf4j
@Component
public class DividendAdvisor {

    private static final int AMOUNT_SCALE = 2;
    private static final int MONEY_SCALE = 0;

    /**
     * 按股息率落区给出加减仓动作与建议金额
     *
     * @param plan          红利参数，可含问题清单
     * @param signal        股息率信号，含数据日期与过期标记
     * @param currentAmount 当前持有金额，单位元，可为 null
     * @return 建议结果，任何信息不全的场景都返回不给动作并说明原因
     */
    public DividendDecision decide(DividendPlan plan, DividendSignal signal, BigDecimal currentAmount) {
        DividendDecision decision = new DividendDecision();
        if (plan == null || !plan.complete()) {
            decision.setAction(DividendAction.UNDECIDABLE);
            if (plan != null && plan.getIssues() != null) {
                decision.getRemarks().addAll(plan.getIssues());
            }
            decision.getRemarks().add("红利参数缺失或不自洽，暂不给加减仓动作");
            return decision;
        }
        if (signal == null || signal.getDividendYield() == null || signal.getDataDate() == null) {
            return undecidable(decision, "没有可用的股息率数据，请先刷新或手工录入后再给建议");
        }
        String freshNote = "股息率 " + plain(signal.getDividendYield()) + "%，基于 " + signal.getDataDate()
                + " 的数据（距今 " + age(signal) + " 天）";
        if (Boolean.TRUE.equals(signal.getStale())) {
            decision.setAction(DividendAction.UNDECIDABLE);
            decision.getRemarks().add(freshNote);
            decision.getRemarks().add(signal.getStaleReason() == null
                    ? "股息率数据已过期，不给加减仓动作" : signal.getStaleReason());
            return decision;
        }
        if (currentAmount == null) {
            return undecidable(decision, "当前持有金额取不到（缺份额或最新净值），暂不给加减仓动作");
        }
        decision.getRemarks().add(freshNote);
        BigDecimal yield = signal.getDividendYield().setScale(4, RoundingMode.HALF_UP);
        BigDecimal base = plan.getBaseAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal floor = plan.getFloorAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal current = currentAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (yield.compareTo(plan.getAddYield()) >= 0) {
            return add(decision, plan, base, current);
        }
        if (yield.compareTo(plan.getReduceYield()) <= 0) {
            return reduce(decision, plan, floor, current);
        }
        decision.setAction(DividendAction.HOLD);
        decision.getRemarks().add("股息率 " + plain(yield) + "% 位于减仓阈值 " + plain(plan.getReduceYield())
                + "% 与加仓阈值 " + plain(plan.getAddYield()) + "% 之间，维持当前仓位");
        decision.setGridSuppressed(Boolean.FALSE);
        return decision;
    }

    /**
     * 加仓分支，补到基准仓位即止，不追高
     *
     * @param decision 输出
     * @param plan     参数
     * @param base     基准仓位金额
     * @param current  当前持有金额
     * @return 建议结果
     */
    private DividendDecision add(DividendDecision decision, DividendPlan plan, BigDecimal base, BigDecimal current) {
        decision.getRemarks().add("股息率不低于加仓阈值 " + plain(plan.getAddYield()) + "%");
        if (current.compareTo(base) >= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("当前持有 " + plain(current) + " 元已不低于基准仓位 " + plain(base)
                    + " 元，股息率虽高也不追高，维持持有");
            return decision;
        }
        BigDecimal suggest = money(base.subtract(current));
        if (suggest.signum() <= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("与基准仓位的差额不足 1 元，按元取整后无实际动作");
            return decision;
        }
        decision.setAction(DividendAction.ADD);
        decision.setTargetAmount(base);
        decision.setSuggestAmount(suggest);
        decision.setRemainingAmount(current.add(suggest));
        decision.setFloorProtected(Boolean.FALSE);
        decision.setGridSuppressed(Boolean.TRUE);
        decision.getRemarks().add("建议加仓 " + plain(suggest) + " 元，加仓后持有 " + plain(decision.getRemainingAmount())
                + " 元（基准仓位）");
        return decision;
    }

    /**
     * 减仓分支，最多减到保底分红金额，触碰护城河时改为持有
     *
     * @param decision 输出
     * @param plan     参数
     * @param floor    保底分红金额
     * @param current  当前持有金额
     * @return 建议结果
     */
    private DividendDecision reduce(DividendDecision decision, DividendPlan plan, BigDecimal floor, BigDecimal current) {
        decision.getRemarks().add("股息率不高于减仓阈值 " + plain(plan.getReduceYield()) + "%");
        if (current.compareTo(floor) <= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setFloorProtected(Boolean.TRUE);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("当前持有 " + plain(current) + " 元已不高于保底分红金额 " + plain(floor)
                    + " 元，减仓会击穿保底，改为持有");
            return decision;
        }
        // 向下取整保证减仓后剩余金额不低于保底分红金额，宁可少卖一元也不越线
        BigDecimal suggest = money(current.subtract(floor));
        if (suggest.signum() <= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setFloorProtected(Boolean.TRUE);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("与保底分红金额的差额不足 1 元，按元取整后无实际动作");
            return decision;
        }
        decision.setAction(DividendAction.REDUCE);
        decision.setTargetAmount(floor);
        decision.setSuggestAmount(suggest);
        decision.setRemainingAmount(current.subtract(suggest));
        decision.setFloorProtected(Boolean.TRUE);
        decision.setGridSuppressed(Boolean.TRUE);
        decision.getRemarks().add("建议减仓 " + plain(suggest) + " 元，减仓后剩余 " + plain(decision.getRemainingAmount())
                + " 元，不低于保底分红金额");
        return decision;
    }

    /**
     * 标记为不给动作并补充原因
     *
     * @param decision 输出
     * @param reason   原因
     * @return 同一结果
     */
    private DividendDecision undecidable(DividendDecision decision, String reason) {
        decision.setAction(DividendAction.UNDECIDABLE);
        decision.setGridSuppressed(Boolean.FALSE);
        decision.getRemarks().add(reason);
        return decision;
    }

    /**
     * 取信号年龄，缺省按 0 处理
     *
     * @param signal 股息率信号
     * @return 天数
     */
    private long age(DividendSignal signal) {
        return signal.getAgeDays() == null ? 0L : signal.getAgeDays();
    }

    /**
     * 金额向下取整到元，保证不越过基准仓位与保底金额
     *
     * @param value 原始金额
     * @return 取整后的金额
     */
    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.DOWN);
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
        // stripTrailingZeros 会把 1000 写成 1E+3，文案里必须回到普通写法
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
