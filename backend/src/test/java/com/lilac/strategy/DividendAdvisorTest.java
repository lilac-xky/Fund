package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.strategy.model.DividendDecision;
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
 * 红利加减仓规则测试，核心是「金额精确到元」「保底不被击穿」「过期不给动作」
 */
class DividendAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private DividendAdvisor advisor;
    private DividendPlan plan;

    /**
     * 装配计算器与一组基准参数：基准 100000、保底 60000、加仓 5%、减仓 3%
     */
    @BeforeEach
    void setUp() {
        advisor = new DividendAdvisor();
        plan = planOf("100000", "60000", "5.00", "3.00");
    }

    /**
     * 股息率高于加仓阈值时补到基准仓位，金额与差额一致
     */
    @Test
    @DisplayName("股息率高于加仓阈值补到基准仓位")
    void addsUpToBaseAmount() {
        DividendDecision decision = advisor.decide(plan, signalOf("6.20"), new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("50000");
        assertThat(decision.getTargetAmount()).isEqualByComparingTo("100000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("100000");
        assertThat(decision.getGridSuppressed()).isTrue();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("建议加仓"));
    }

    /**
     * 建议金额取整到元，零头向下截断，加仓后不会超过基准仓位
     */
    @Test
    @DisplayName("建议金额精确到元且向下取整")
    void amountRoundsToYuan() {
        DividendDecision decision = advisor.decide(plan, signalOf("5.10"), new BigDecimal("50000.789"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.ADD);
        // 100000 − 50000.789 = 49999.211，向下取整得 49999
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("49999");
        assertThat(decision.getSuggestAmount().scale()).isZero();
        assertThat(decision.getRemainingAmount()).isLessThanOrEqualTo(new BigDecimal("100000"));
    }

    /**
     * 已持有超过基准仓位时，股息率再高也只持有，不追高
     */
    @Test
    @DisplayName("已超基准仓位时股息率高也不追")
    void doesNotChaseAboveBase() {
        DividendDecision decision = advisor.decide(plan, signalOf("7.00"), new BigDecimal("130000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("不追高"));
    }

    /**
     * 股息率低于减仓阈值时减到保底分红金额，减仓后剩余不低于保底
     */
    @Test
    @DisplayName("股息率低于减仓阈值减到保底分红金额")
    void reducesDownToFloor() {
        DividendDecision decision = advisor.decide(plan, signalOf("2.10"), new BigDecimal("100000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.REDUCE);
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("40000");
        assertThat(decision.getRemainingAmount()).isEqualByComparingTo("60000");
        assertThat(decision.getRemainingAmount()).isGreaterThanOrEqualTo(plan.getFloorAmount());
        assertThat(decision.getFloorProtected()).isTrue();
        assertThat(decision.getGridSuppressed()).isTrue();
    }

    /**
     * 减仓会击穿保底金额时改为持有，并说明是保底护城河拦下
     */
    @Test
    @DisplayName("减仓会击穿保底金额时改为持有")
    void holdsWhenFloorWouldBreak() {
        DividendDecision decision = advisor.decide(plan, signalOf("1.50"), new BigDecimal("58000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getFloorProtected()).isTrue();
        assertThat(decision.getGridSuppressed()).isFalse();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("减仓会击穿保底"));
    }

    /**
     * 持有金额正好等于保底金额时不减仓，边界不能越线
     */
    @Test
    @DisplayName("持有金额等于保底金额时持有")
    void holdsAtExactFloor() {
        DividendDecision decision = advisor.decide(plan, signalOf("0.50"), new BigDecimal("60000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getFloorProtected()).isTrue();
    }

    /**
     * 股息率在阈值区间内时维持当前仓位，不给金额
     */
    @Test
    @DisplayName("股息率在阈值区间内维持持有")
    void holdsInsideBand() {
        DividendDecision decision = advisor.decide(plan, signalOf("4.00"), new BigDecimal("80000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getGridSuppressed()).isFalse();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("位于减仓阈值"));
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
        DividendPlan invalid = planOf("100000", "120000", "5.00", "3.00");
        invalid.setIssues(List.of("保底分红金额 120000 元不得高于基准仓位 100000 元"));

        DividendDecision decision = advisor.decide(invalid, signalOf("6.20"), new BigDecimal("50000"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(decision.getRemarks()).contains("保底分红金额 120000 元不得高于基准仓位 100000 元");
    }

    /**
     * 与基准仓位的差额不足一元时无实际动作，不会给出 0 元建议
     */
    @Test
    @DisplayName("差额不足一元时无动作")
    void tinyGapYieldsHold() {
        DividendDecision decision = advisor.decide(plan, signalOf("6.20"), new BigDecimal("99999.60"));

        assertThat(decision.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("不足 1 元"));
    }

    /**
     * 构造参数
     *
     * @param base         基准仓位金额
     * @param floor        保底分红金额
     * @param addYield     加仓阈值
     * @param reduceYield  减仓阈值
     * @return 参数
     */
    private DividendPlan planOf(String base, String floor, String addYield, String reduceYield) {
        DividendPlan built = new DividendPlan();
        built.setBaseAmount(new BigDecimal(base));
        built.setFloorAmount(new BigDecimal(floor));
        built.setAddYield(new BigDecimal(addYield));
        built.setReduceYield(new BigDecimal(reduceYield));
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
