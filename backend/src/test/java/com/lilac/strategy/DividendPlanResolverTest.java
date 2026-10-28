package com.lilac.strategy;

import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.enums.ParamGroup;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.DividendPlan;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 红利参数校验测试，核心是「保底 > 基准」与「阈值顺序颠倒」都必须被拒绝
 */
@SpringBootTest
@Transactional
class DividendPlanResolverTest {

    @Autowired
    private DividendPlanResolver resolver;

    @Autowired
    private StrategyParamService strategyParamService;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    /**
     * 清掉红利分组参数，避免与上一次用例串数据
     */
    @BeforeEach
    void setUp() {
        strategyParamMapper.deleteByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()));
    }

    /**
     * 空库时四条参数都报未设置，不给默认值蒙混过关
     */
    @Test
    @DisplayName("空库时返回缺项清单")
    void emptyPlanListsAllMissing() {
        DividendPlan plan = resolver.resolve();

        assertThat(plan.complete()).isFalse();
        assertThat(plan.getIssues()).hasSize(4);
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("基准仓位金额未设置"));
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("保底分红金额未设置"));
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("加仓股息率阈值未设置"));
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("减仓股息率阈值未设置"));
    }

    /**
     * 合法参数能保存并读回，四项数值一致
     */
    @Test
    @DisplayName("合法参数可保存并读回")
    void validPlanRoundTrips() {
        resolver.save(planOf("120000", "70000", "5.20", "3.10"));

        DividendPlan saved = resolver.resolve();
        assertThat(saved.complete()).isTrue();
        assertThat(saved.getIssues()).isEmpty();
        assertThat(saved.getBaseAmount()).isEqualByComparingTo("120000");
        assertThat(saved.getFloorAmount()).isEqualByComparingTo("70000");
        assertThat(saved.getAddYield()).isEqualByComparingTo("5.20");
        assertThat(saved.getReduceYield()).isEqualByComparingTo("3.10");
        // 同一分组同一参数名只留一行，重复保存走更新
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()))).isEqualTo(4);
    }

    /**
     * 保底高于基准时必须拒绝保存并指出两个字段
     */
    @Test
    @DisplayName("保底高于基准时拒绝保存")
    void floorAboveBaseRejected() {
        List<String> issues = resolver.validate(planOf("100000", "150000", "5.00", "3.00"));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("保底分红金额").contains("不得高于基准仓位");
    }

    /**
     * 减仓阈值不低于加仓阈值时拒绝保存，否则区间不存在
     */
    @Test
    @DisplayName("减仓阈值不低于加仓阈值时拒绝保存")
    void reversedBandRejected() {
        assertThat(resolver.validate(planOf("100000", "60000", "3.00", "3.00")))
                .anyMatch(text -> text.contains("必须低于加仓阈值"));
        assertThat(resolver.validate(planOf("100000", "60000", "2.00", "4.00")))
                .anyMatch(text -> text.contains("必须低于加仓阈值"));
    }

    /**
     * 金额为 0 或负数、股息率越界都必须拒绝
     */
    @Test
    @DisplayName("非正金额与越界股息率被拒绝")
    void nonPositiveAndOutOfRangeRejected() {
        assertThat(resolver.validate(planOf("0", "0", "5.00", "3.00")))
                .anyMatch(text -> text.contains("基准仓位金额必须大于 0"))
                .anyMatch(text -> text.contains("保底分红金额必须大于 0"));
        assertThat(resolver.validate(planOf("100000", "60000", "0", "3.00")))
                .anyMatch(text -> text.contains("应在 0~100"));
        assertThat(resolver.validate(planOf("100000", "60000", "150", "3.00")))
                .anyMatch(text -> text.contains("应在 0~100"));
    }

    /**
     * 库内参数被写成非数值时，读取不抛异常，而是转成缺项清单
     */
    @Test
    @DisplayName("库内非法数值不被静默接受")
    void invalidStoredValueBecomesIssue() {
        ParamSaveRequest bad = new ParamSaveRequest();
        bad.setParamGroup(ParamGroup.DIVIDEND);
        bad.setParamKey("baseAmount");
        bad.setParamValue("十万");
        strategyParamService.saveParam(bad);
        resolver.save(planOf("100000", "60000", "5.00", "3.00"));
        strategyParamService.saveParam(bad);

        DividendPlan plan = resolver.resolve();

        assertThat(plan.complete()).isFalse();
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("基准仓位金额"));
    }

    /**
     * 构造参数
     *
     * @param base        基准仓位金额
     * @param floor       保底分红金额
     * @param addYield    加仓阈值
     * @param reduceYield 减仓阈值
     * @return 参数
     */
    private DividendPlan planOf(String base, String floor, String addYield, String reduceYield) {
        DividendPlan plan = new DividendPlan();
        plan.setBaseAmount(new BigDecimal(base));
        plan.setFloorAmount(new BigDecimal(floor));
        plan.setAddYield(new BigDecimal(addYield));
        plan.setReduceYield(new BigDecimal(reduceYield));
        return plan;
    }
}
