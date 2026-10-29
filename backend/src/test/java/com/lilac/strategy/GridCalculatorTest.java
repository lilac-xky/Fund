package com.lilac.strategy;

import com.lilac.enums.DividendAction;
import com.lilac.enums.GridAction;
import com.lilac.strategy.model.GridDecision;
import com.lilac.strategy.model.GridPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网格规则测试，核心是「划格正确」「穿越方向正确」「未穿越不提醒」「红利互斥」
 */
class GridCalculatorTest {

    private GridCalculator calculator;
    private GridPlan plan;

    /**
     * 装配计算器与一组基准参数：下界 1.00、上界 1.50、格子 0.10、每格 1000
     */
    @BeforeEach
    void setUp() {
        calculator = new GridCalculator();
        plan = planOf("1.00", "1.50", "0.10", "1000");
    }

    /**
     * 按上下界与格子大小列出网格线，上界本身也算一条
     */
    @Test
    @DisplayName("按上下界与格子大小划出网格线")
    void listsGridLines() {
        List<BigDecimal> lines = calculator.lines(plan);

        // 1.00 ~ 1.50 每 0.10 一格，共 6 条，上界恰好落在网格上不重复追加
        assertThat(lines).hasSize(6);
        assertThat(lines.get(0)).isEqualByComparingTo("1.00");
        assertThat(lines.get(lines.size() - 1)).isEqualByComparingTo("1.50");
    }

    /**
     * 上下界除不尽时补上界那条，保证摸到上界时有可卖的线
     */
    @Test
    @DisplayName("除不尽时补上界那条网格线")
    void appendsUpperBoundWhenNotDivisible() {
        List<BigDecimal> lines = calculator.lines(planOf("1.00", "1.55", "0.10", "1000"));

        assertThat(lines.get(lines.size() - 1)).isEqualByComparingTo("1.55");
        assertThat(lines).hasSize(7);
    }

    /**
     * 当前价定位到正确格子，下界那一格序号为 0
     */
    @Test
    @DisplayName("当前价定位到正确格子")
    void locatesCurrentGrid() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.24"), new BigDecimal("1.24"), null);

        assertThat(decision.getCurrentGrid()).isEqualTo(2);
        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
    }

    /**
     * 价格下穿网格线时建议买入，金额为每格金额
     */
    @Test
    @DisplayName("下穿网格线时建议买入")
    void crossingDownTriggersBuy() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.18"), new BigDecimal("1.22"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.BUY);
        assertThat(decision.getCrossedLine()).isEqualByComparingTo("1.20");
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("1000");
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("下穿"));
    }

    /**
     * 价格上穿网格线时建议卖出，金额为每格金额
     */
    @Test
    @DisplayName("上穿网格线时建议卖出")
    void crossingUpTriggersSell() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.32"), new BigDecimal("1.28"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.SELL);
        assertThat(decision.getCrossedLine()).isEqualByComparingTo("1.30");
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("1000");
    }

    /**
     * 一次越过三条网格线时金额按格数累加，不拆成多次提醒
     */
    @Test
    @DisplayName("一次越过多个格子时金额累加")
    void crossingMultipleLinesAccumulates() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.18"), new BigDecimal("1.48"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.BUY);
        // 1.50 那一侧不含参考价，1.20 / 1.30 / 1.40 三条穿越
        assertThat(decision.getSuggestAmount()).isEqualByComparingTo("3000");
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("共越过 3 条"));
    }

    /**
     * 参考价与当前价之间没有网格线时不产生提醒，这是「未触及时不提醒」的正面用例
     */
    @Test
    @DisplayName("未穿越网格线时不产生提醒")
    void noCrossingYieldsNone() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.21"), new BigDecimal("1.23"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getCrossedLine()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("没有网格线被穿越"));
    }

    /**
     * 参考价恰好压在网格线上时那条线不算穿越，避免同一条线反复触发
     */
    @Test
    @DisplayName("参考价压在网格线上时该线不算穿越")
    void referenceOnLineIsNotCrossed() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.19"), new BigDecimal("1.20"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
    }

    /**
     * 同标的有红利加仓建议时网格让位，不给出反向动作
     */
    @Test
    @DisplayName("同标的有红利建议时网格让位")
    void dividendAdviceSuppressesGrid() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.18"), new BigDecimal("1.22"),
                DividendAction.ADD);

        assertThat(decision.getAction()).isEqualTo(GridAction.SUPPRESSED);
        assertThat(decision.getSuppressed()).isTrue();
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("让位"));
    }

    /**
     * 红利给的是不给动作时不影响网格，两者互不干扰
     */
    @Test
    @DisplayName("红利不给动作时网格照常触发")
    void undecidableDividendDoesNotSuppress() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.18"), new BigDecimal("1.22"),
                DividendAction.UNDECIDABLE);

        assertThat(decision.getAction()).isEqualTo(GridAction.BUY);
        assertThat(decision.getSuppressed()).isFalse();
    }

    /**
     * 价格跑出上界时仍按穿越判断，但必须提示已超出区间需重新划格
     */
    @Test
    @DisplayName("价格超出上界时提示重新划格")
    void outOfRangeWarns() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.70"), new BigDecimal("1.45"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.SELL);
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("已高于网格上界"));
    }

    /**
     * 参数不全时不计算也不给数字，只说明原因
     */
    @Test
    @DisplayName("参数不全时不计算网格")
    void incompletePlanYieldsNone() {
        GridPlan invalid = planOf("1.00", "1.50", "0.10", "1000");
        invalid.setIssues(List.of("每格金额未设置"));

        GridDecision decision = calculator.decide(invalid, new BigDecimal("1.18"), new BigDecimal("1.22"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
        assertThat(decision.getSuggestAmount()).isNull();
        assertThat(decision.getRemarks()).contains("每格金额未设置");
    }

    /**
     * 缺参考价时不判断穿越，避免把开盘价当成穿越依据
     */
    @Test
    @DisplayName("缺参考价时不给提醒")
    void missingReferenceYieldsNone() {
        GridDecision decision = calculator.decide(plan, new BigDecimal("1.18"), null, null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("缺参考价"));
    }

    /**
     * 当前价取不到时不给提醒
     */
    @Test
    @DisplayName("当前价缺失时不给提醒")
    void missingPriceYieldsNone() {
        GridDecision decision = calculator.decide(plan, null, new BigDecimal("1.22"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("当前价取不到"));
    }

    /**
     * 划不出两格时视为参数不可用，网格退化成价格点没有意义
     */
    @Test
    @DisplayName("格子过少时不产生提醒")
    void tooFewLinesYieldsNone() {
        GridPlan narrow = planOf("1.00", "1.05", "0.10", "1000");

        GridDecision decision = calculator.decide(narrow, new BigDecimal("1.02"), new BigDecimal("1.04"), null);

        assertThat(decision.getAction()).isEqualTo(GridAction.NONE);
        assertThat(decision.getRemarks()).anyMatch(text -> text.contains("至少需要 2 格"));
    }

    /**
     * 构造网格参数
     *
     * @param lower  价格下界
     * @param upper  价格上界
     * @param step   格子大小
     * @param amount 每格金额
     * @return 参数
     */
    private GridPlan planOf(String lower, String upper, String step, String amount) {
        GridPlan built = new GridPlan();
        built.setLowerBound(new BigDecimal(lower));
        built.setUpperBound(new BigDecimal(upper));
        built.setStep(new BigDecimal(step));
        built.setAmountPerGrid(new BigDecimal(amount));
        return built;
    }
}
