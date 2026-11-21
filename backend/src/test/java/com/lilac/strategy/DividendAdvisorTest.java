package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.strategy.model.DividendDecision;
import com.lilac.strategy.model.DividendLadderRung;
import com.lilac.strategy.model.DividendPlan;
import com.lilac.strategy.model.DividendSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 红利阶梯规则测试，核心是「按股息率命中的档位补差额」「最低档只加不减」「过期不给动作」
 */
class DividendAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private DividendAdvisor advisor;
    private DividendPlan plan;

    /**
     * 装配计算器与一条四档金字塔：6.0%→10 万、5.5%→8 万、5.0%→6 万、4.5%→3 万
     */
    @BeforeEach
    void setUp() {
        advisor = new DividendAdvisor();
        plan = planOf(rung("6.00", "100000"), rung("5.50", "80000"),
                rung("5.00", "60000"), rung("4.50", "30000"));
    }

    /**
     * 股息率命中某档时补到该档金额，差多少补多少，不是固定金额
     */
    @Test
    @DisplayName("股息率命中档位时补到该档金额")
    void addsUpToHitRung() {
        DividendDecision decision = advisor.decide(plan, signalOf("5.60"), new BigDecimal("50000"));

        // 5.60% 命中 5.50% 档（8 万），差额 3 万
        assertThat(decision.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(decision.getHitYield()).isEqualByComparingTo("5.50");
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("80000");
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("80000");
        assertThat(decision.getGridSuppressed()).isTrue();
    }

    /**
     * 阶梯的幂等性：已经是该档金额时不再买，反复跑不会重复投入
     */
    @Test
    @DisplayName("已达该档金额时持有不再加仓")
    void holdsAtHitRungAmount() {
        DividendDecision decision = advisor.decide(plan, signalOf("5.60"), new BigDecimal("80000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("一致"));
    }

    /**
     * 股息率跳档时直接补到位，不是只补最近一档
     */
    @Test
    @DisplayName("股息率跳档时一次补到位")
    void jumpsStraightToRung() {
        DividendDecision decision = advisor.decide(plan, signalOf("6.30"), new BigDecimal("10000"));

        // 6.30% 直接命中最高档 6.0%（10 万），不是逐档 3 万→6 万→8 万→10 万
        assertThat(decision.getHitYield()).isEqualByComparingTo("6.00");
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("90000");
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("100000");
    }

    /**
     * 股息率走低时减到该档金额
     */
    @Test
    @DisplayName("股息率走低时减到该档金额")
    void reducesDownToRung() {
        DividendDecision decision = advisor.decide(plan, signalOf("4.60"), new BigDecimal("100000"));

        // 4.60% 命中 4.50% 档（3 万），减 7 万
        assertThat(decision.getAction()).isEqualTo(DividendAction.REDUCE);
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("70000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getGridSuppressed()).isTrue();
    }

    /**
     * 最低一档就是保底：减到它为止，不会更少
     */
    @Test
    @DisplayName("最低档就是保底，减到它为止")
    void bottomRungIsTheFloor() {
        DividendDecision decision = advisor.decide(plan, signalOf("4.60"), new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.REDUCE);
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getFloorProtected()).isTrue();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("已到保底底仓"));
    }

    /**
     * 最低档金额不足时仍然加仓，保底不是“不能买”的上限
     */
    @Test
    @DisplayName("最低档金额不足时仍补仓")
    void bottomRungStillAdds() {
        DividendDecision decision = advisor.decide(plan, signalOf("4.60"), new BigDecimal("10000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("20000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("30000");
    }

    /**
     * 股息率跌破阶梯最低门槛时按住最低一档，不会因为股息率极低就要求清仓
     */
    @Test
    @DisplayName("股息率低于最低档时按住最低档")
    void belowLadderHoldsBottomRung() {
        DividendDecision decision = advisor.decide(plan, signalOf("4.00"), new BigDecimal("30000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("30000");
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("按最低一档的保底金额"));
    }

    /**
     * 建议金额取整到元，零头向下截断，加仓后不会超过该档金额
     */
    @Test
    @DisplayName("建议金额精确到元且向下取整")
    void amountRoundsToYuan() {
        DividendDecision decision = advisor.decide(plan, signalOf("5.60"), new BigDecimal("50000.789"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.ADD);
        // 80000 − 50000.789 = 29999.211，向下取整得 29999
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("29999");
        assertThat(decision.getSuggestAmount().scale()).isZero();
        assertThat(decision.getRemainingAmount()).isLessThanOrEqualTo(new BigDecimal("80000"));
    }

    /**
     * 与目标金额的差额不足一元时无实际动作，不会给出 0 元建议
     */
    @Test
    @DisplayName("差额不足一元时无动作")
    void tinyGapYieldsHold() {
        DividendDecision decision = advisor.decide(plan, signalOf("5.60"), new BigDecimal("79999.60"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("不足 1 元"));
    }

    /**
     * 股息率数据过期时不给动作，但必须带出数据日期与距今已过天数
     */
    @Test
    @DisplayName("股息率过期时不给动作并标注数据日期")
    void staleSignalYieldsNoAction() {
        DividendSignal signal = signalOf("6.20");
        signal.setDataDate(TODAY.minusDays(20));
        signal.setAgeDays(20L);
        signal.setStale(Boolean.TRUE);
        signal.setStaleReason("数据日期 " + signal.getDataDate() + "，已过 20 天，超过 7 天阈值，不得据此给出加减仓动作");

        DividendDecision decision = advisor.decide(plan, signal, new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getTargetAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("2026-10-08"));
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("超过 7 天阈值"));
    }

    /**
     * 股息率缺失时不动作，提示先刷新或手工录入
     */
    @Test
    @DisplayName("股息率缺失时不给动作")
    void missingYieldYieldsNoAction() {
        DividendDecision decision = advisor.decide(plan, null, new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("没有可用的股息率"));
    }

    /**
     * 当前金额取不到时不动作，绝不按 0 元推算建议金额
     */
    @Test
    @DisplayName("当前金额未知时不给动作")
    void unknownAmountYieldsNoAction() {
        DividendDecision decision = advisor.decide(plan, signalOf("6.20"), null);

        assertThat(decision.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("当前持有金额取不到"));
    }

    /**
     * 参数含问题清单时不给动作，并原样带出问题
     */
    @Test
    @DisplayName("参数不合法时不给动作")
    void invalidPlanYieldsNoAction() {
        DividendPlan invalid = planOf(rung("5.00", "60000"));
        invalid.setIssues(List.of("股息率阶梯只有 1 档，至少需要 2 档，否则表达不了越跌越买"));

        DividendDecision decision = advisor.decide(invalid, signalOf("6.20"), new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(decision.getRemarks()).contains("股息率阶梯只有 1 档，至少需要 2 档，否则表达不了越跌越买");
    }

    /**
     * 阶梯乱序传入时按股息率自动排序，不要求调用方预先排好
     */
    @Test
    @DisplayName("乱序阶梯按股息率自动排序")
    void unsortedLadderIsNormalized() {
        DividendPlan unordered = planOf(rung("4.50", "30000"), rung("6.00", "100000"),
                rung("5.00", "60000"));

        DividendDecision decision = advisor.decide(unordered, signalOf("5.10"), new BigDecimal("10000"));

        assertThat(decision.getHitYield()).isEqualByComparingTo("5.00");
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("60000");
    }

    /**
     * 构造一档
     *
     * @param yield  股息率门槛
     * @param amount 应持有金额
     * @return 档位
     */
    private DividendLadderRung rung(String yield, String amount) {
        DividendLadderRung rung = new DividendLadderRung();
        rung.setYield(new BigDecimal(yield));
        rung.setAmount(new BigDecimal(amount));
        return rung;
    }

    /**
     * 构造参数
     *
     * @param rungs 阶梯
     * @return 参数
     */
    private DividendPlan planOf(DividendLadderRung... rungs) {
        DividendPlan built = new DividendPlan();
        built.setLadder(new java.util.ArrayList<>(List.of(rungs)));
        return built;
    }

    /**
     * 构造未过期的股息率信号
     *
     * @param yield 股息率，百分点
     * @return 信号
     */
    private DividendSignal signalOf(String yield) {
        DividendSignal signal = new DividendSignal();
        signal.setDividendYield(new BigDecimal(yield));
        signal.setDataDate(TODAY.minusDays(1));
        signal.setAgeDays(1L);
        signal.setStale(Boolean.FALSE);
        return signal;
    }
}
