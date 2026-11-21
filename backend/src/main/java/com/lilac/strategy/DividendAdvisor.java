package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.strategy.model.DividendDecision;
import com.lilac.strategy.model.DividendLadderRung;
import com.lilac.strategy.model.DividendPlan;
import com.lilac.strategy.model.DividendSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 红利加减仓规则计算器，按股息率定位阶梯档位并算出与应持有金额的差额，金额一律向下取整到元
 */
@Slf4j
@Component
public class DividendAdvisor {

    private static final int AMOUNT_SCALE = 2;
    private static final int MONEY_SCALE = 0;

    /**
     * 按股息率命中的阶梯档位给出加减仓动作与建议金额
     *
     * @param plan          红利参数，含阶梯与问题清单
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
        BigDecimal current = currentAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        decision.setCurrentAmount(current);
        Rung hit = hitRung(plan.sortedLadder(), yield);
        decision.setHitYield(hit.yield());
        decision.setTargetAmount(hit.amount());
        decision.getRemarks().add(hit.note());
        // 阶梯最低一档就是保底：任何档位的目标金额都不会低于它，减仓自然不会击穿
        boolean atFloor = hit.amount().compareTo(plan.bottomAmount()) == 0;
        if (current.compareTo(hit.amount()) > 0) {
            return reduce(decision, hit.amount(), current, atFloor);
        }
        if (current.compareTo(hit.amount()) < 0) {
            return add(decision, hit.amount(), current);
        }
        decision.setAction(DividendAction.HOLD);
        decision.setFloorProtected(atFloor);
        decision.setGridSuppressed(Boolean.FALSE);
        decision.getRemarks().add("当前持有 " + plain(current) + " 元与这一档的应持有金额一致，不需要动作");
        return decision;
    }

    /**
     * 找出当前股息率命中的档位；跌破最低门槛时按住最低一档，不会因为股息率极低就要求清仓
     *
     * @param ladder 降序阶梯
     * @param yield  当前股息率
     * @return 命中的档，恒不为 null
     */
    private Rung hitRung(List<DividendLadderRung> ladder, BigDecimal yield) {
        for (DividendLadderRung rung : ladder) {
            if (yield.compareTo(rung.getYield()) >= 0) {
                return new Rung(rung.getYield(), rung.getAmount(),
                        "股息率 " + plain(yield) + "% 达到阶梯的 " + plain(rung.getYield()) + "% 档，"
                                + "这一档的应持有金额是 " + plain(rung.getAmount()) + " 元");
            }
        }
        DividendLadderRung bottom = ladder.get(ladder.size() - 1);
        return new Rung(bottom.getYield(), bottom.getAmount(),
                "股息率 " + plain(yield) + "% 低于阶梯最低的 " + plain(bottom.getYield())
                        + "% 档，按最低一档的保底金额 " + plain(bottom.getAmount()) + " 元处理");
    }

    /**
     * 加仓分支，补到该档应持有金额即止
     *
     * @param decision 输出
     * @param target   该档应持有金额
     * @param current  当前持有金额
     * @return 建议结果
     */
    private DividendDecision add(DividendDecision decision, BigDecimal target, BigDecimal current) {
        // 向下取整保证加仓后不会超过该档的应持有金额，宁可少买一元也不越线
        BigDecimal suggest = money(target.subtract(current));
        if (suggest.signum() <= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setFloorProtected(Boolean.FALSE);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("与该档应持有金额的差额不足 1 元，按元取整后无实际动作");
            return decision;
        }
        decision.setAction(DividendAction.ADD);
        decision.setSuggestAmount(suggest);
        decision.setRemainingAmount(current.add(suggest));
        decision.setFloorProtected(Boolean.FALSE);
        decision.setGridSuppressed(Boolean.TRUE);
        decision.getRemarks().add("当前持有 " + plain(current) + " 元低于这一档的 " + plain(target)
                + " 元，建议加仓 " + plain(suggest) + " 元，加仓后持有 "
                + plain(decision.getRemainingAmount()) + " 元");
        return decision;
    }

    /**
     * 减仓分支，减到该档应持有金额即止
     *
     * @param decision 输出
     * @param target   该档应持有金额
     * @param current  当前持有金额
     * @param atFloor  这一档是否就是最低一档
     * @return 建议结果
     */
    private DividendDecision reduce(DividendDecision decision, BigDecimal target, BigDecimal current,
                                    boolean atFloor) {
        // 向下取整保证减仓后剩余金额不低于该档的应持有金额，宁可少卖一元也不越线
        BigDecimal suggest = money(current.subtract(target));
        if (suggest.signum() <= 0) {
            decision.setAction(DividendAction.HOLD);
            decision.setFloorProtected(atFloor);
            decision.setGridSuppressed(Boolean.FALSE);
            decision.getRemarks().add("与该档应持有金额的差额不足 1 元，按元取整后无实际动作");
            return decision;
        }
        decision.setAction(DividendAction.REDUCE);
        decision.setSuggestAmount(suggest);
        decision.setRemainingAmount(current.subtract(suggest));
        decision.setFloorProtected(atFloor);
        decision.setGridSuppressed(Boolean.TRUE);
        decision.getRemarks().add("当前持有 " + plain(current) + " 元高于这一档的 " + plain(target)
                + " 元，建议减仓 " + plain(suggest) + " 元，减仓后剩余 "
                + plain(decision.getRemainingAmount()) + " 元"
                + (atFloor ? "（已到保底底仓，不会再往下减）" : ""));
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
     * 金额向下取整到元，保证不越过该档的应持有金额
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

    /**
     * 命中的档位，带一句可读说明
     *
     * @param yield  该档股息率
     * @param amount 该档应持有金额
     * @param note   命中说明
     */
    private record Rung(BigDecimal yield, BigDecimal amount, String note) {
    }
}
