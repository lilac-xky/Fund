package com.lilac.strategy;

import com.lilac.enums.ParamGroup;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.strategy.model.GridPlan;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 网格参数按标的存储测试，核心是两只 ETF 的价格区间互不覆盖
 */
@SpringBootTest
@Transactional
class GridPlanResolverTest {

    private static final String ETF_A = "ETF:510300";
    private static final String ETF_B = "ETF:512890";

    @Autowired
    private GridPlanResolver resolver;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    /**
     * 清掉网格分组参数，避免与上一次用例串数据
     */
    @BeforeEach
    void setUp() {
        strategyParamMapper.deleteByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.GRID.name()));
    }

    /**
     * 两只标的各存一套参数，读回时互不覆盖
     */
    @Test
    @DisplayName("按标的各存一套且互不覆盖")
    void storesPerVariety() {
        resolver.save(planOf(ETF_A, "3.60", "4.40", "0.05", "1000"));
        resolver.save(planOf(ETF_B, "1.00", "1.40", "0.02", "500"));

        GridPlan a = resolver.resolveFor(ETF_A, "沪深300ETF");
        GridPlan b = resolver.resolveFor(ETF_B, "红利低波ETF");

        assertThat(a.getLowerBound()).isEqualByComparingTo("3.6");
        assertThat(a.getAmountPerGrid()).isEqualByComparingTo("1000");
        assertThat(b.getLowerBound()).isEqualByComparingTo("1.0");
        assertThat(b.getAmountPerGrid()).isEqualByComparingTo("500");
        assertThat(a.getVarietyName()).isEqualTo("沪深300ETF");
        assertThat(b.getVarietyKey()).isEqualTo(ETF_B);
    }

    /**
     * 未单独配置的标的回落通用参数，并明确提示这是共用的
     */
    @Test
    @DisplayName("未配置的标的回落通用参数并说明来源")
    void fallsBackToGlobal() {
        resolver.saveGlobal(planOf(null, "3.00", "4.00", "0.10", "800"));

        GridPlan fallback = resolver.resolveFor("ETF:999999", "另一只ETF");

        assertThat(fallback.complete()).isTrue();
        assertThat(fallback.getLowerBound()).isEqualByComparingTo("3.0");
        // 提示走 notes，不能进 issues，否则会被当成参数不可用而算不出网格
        assertThat(fallback.getNotes()).anyMatch(text -> text.contains("沿用"));
        assertThat(fallback.getIssues()).isEmpty();
    }

    /**
     * 单独配置过的标的不会因为存在通用参数而回落到通用值
     */
    @Test
    @DisplayName("单独配置优先于通用参数")
    void customOverridesGlobal() {
        resolver.saveGlobal(planOf(null, "3.00", "4.00", "0.10", "800"));
        resolver.save(planOf(ETF_A, "3.60", "4.40", "0.05", "1000"));

        GridPlan custom = resolver.resolveFor(ETF_A, "沪深300ETF");

        assertThat(custom.getLowerBound()).isEqualByComparingTo("3.6");
        assertThat(custom.getIssues()).noneMatch(text -> text.contains("沿用"));
    }

    /**
     * 上下界颠倒与只划得出一格都必须被拒绝
     */
    @Test
    @DisplayName("非法参数拒绝保存")
    void rejectsInvalidPlan() {
        assertThatThrownBy(() -> resolver.save(planOf(ETF_A, "4.40", "3.60", "0.05", "1000")))
                .hasMessageContaining("必须大于下界");
        assertThatThrownBy(() -> resolver.save(planOf(ETF_A, "3.60", "4.40", "0.50", "1000")))
                .hasMessageContaining("至少需要");
    }

    /**
     * 保存时没有指定标的必须被拒绝，否则会写出无处挂靠的键
     */
    @Test
    @DisplayName("保存必须指定标的")
    void requiresVarietyKey() {
        assertThatThrownBy(() -> resolver.save(planOf(null, "3.60", "4.40", "0.05", "1000")))
                .hasMessageContaining("必须指定要配置的标的");
    }

    /**
     * 已配置的标的能被全部列出，供页面逐只编辑
     */
    @Test
    @DisplayName("列出全部已配置的标的")
    void listsConfigured() {
        resolver.save(planOf(ETF_A, "3.60", "4.40", "0.05", "1000"));
        resolver.save(planOf(ETF_B, "1.00", "1.40", "0.02", "500"));

        List<GridPlan> all = resolver.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(GridPlan::getVarietyKey).containsExactly(ETF_A, ETF_B);
    }

    /**
     * 构造一组参数
     *
     * @param varietyKey 品种业务主键，可为 null
     * @param lower      下界
     * @param upper      上界
     * @param step       格子大小
     * @param amount     每格金额
     * @return 参数
     */
    private GridPlan planOf(String varietyKey, String lower, String upper, String step, String amount) {
        GridPlan plan = new GridPlan();
        plan.setVarietyKey(varietyKey);
        plan.setLowerBound(new BigDecimal(lower));
        plan.setUpperBound(new BigDecimal(upper));
        plan.setStep(new BigDecimal(step));
        plan.setAmountPerGrid(new BigDecimal(amount));
        return plan;
    }
}
