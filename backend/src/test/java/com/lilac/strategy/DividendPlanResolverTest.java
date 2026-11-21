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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红利参数按基金存储与校验测试，核心是「两只基金互不覆盖」与「保底 > 基准」必须被拒绝
 */
@SpringBootTest
@Transactional
class DividendPlanResolverTest {

    private static final String FUND_A = "OF:007467";
    private static final String FUND_B = "OF:008164";

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
     * 两只基金各存一套参数，读回时互不覆盖，这是本组测试的核心
     */
    @Test
    @DisplayName("按基金各存一套且互不覆盖")
    void storesPerVariety() {
        resolver.save(planOf(FUND_A, "120000", "70000", "5.20", "3.10"));
        resolver.save(planOf(FUND_B, "50000", "20000", "4.50", "2.50"));

        DividendPlan a = resolver.resolveFor(FUND_A, "华泰柏瑞中证红利低波ETF联接C");
        DividendPlan b = resolver.resolveFor(FUND_B, "南方标普红利低波50ETF联接C");

        assertThat(a.getBaseAmount()).isEqualByComparingTo("120000");
        assertThat(a.getReduceYield()).isEqualByComparingTo("3.10");
        assertThat(b.getBaseAmount()).isEqualByComparingTo("50000");
        assertThat(b.getAddYield()).isEqualByComparingTo("4.50");
        assertThat(a.getVarietyName()).isEqualTo("华泰柏瑞中证红利低波ETF联接C");
        // 每只四条参数，两只共八条，键上带品种前缀
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()))).isEqualTo(8);
    }

    /**
     * 没配过的基金返回缺项清单，不拿别的基金的值顶上
     */
    @Test
    @DisplayName("没配过的基金返回缺项清单")
    void unconfiguredFundListsAllMissing() {
        resolver.save(planOf(FUND_A, "120000", "70000", "5.20", "3.10"));

        DividendPlan other = resolver.resolveFor("OF:999999", "另一只红利基金");

        assertThat(other.complete()).isFalse();
        assertThat(other.getIssues()).hasSize(4);
        assertThat(other.getIssues()).anyMatch(text -> text.contains("基准仓位金额未设置"));
        assertThat(other.getIssues()).anyMatch(text -> text.contains("减仓股息率阈值未设置"));
        assertThat(other.getBaseAmount()).isNull();
    }

    /**
     * 旧版全局键齐全时仍能被读到，但会明确提示这只基金还没单独配置
     */
    @Test
    @DisplayName("旧版全局键齐全时仍可读且提示需重新设定")
    void legacyGlobalKeysStillReadable() {
        strategyParamService.saveParam(legacyOf("baseAmount", "100000"));
        strategyParamService.saveParam(legacyOf("floorAmount", "60000"));
        strategyParamService.saveParam(legacyOf("addYield", "5.00"));
        strategyParamService.saveParam(legacyOf("reduceYield", "3.00"));

        DividendPlan plan = resolver.resolveFor(FUND_A, "华泰柏瑞中证红利低波ETF联接C");

        assertThat(plan.complete()).isTrue();
        assertThat(plan.getBaseAmount()).isEqualByComparingTo("100000");
        // 提示走 notes，不能进 issues，否则会被当成参数不可用
        assertThat(plan.getNotes()).anyMatch(text -> text.contains("沿用旧版"));
        assertThat(plan.getIssues()).isEmpty();
    }

    /**
     * 旧版全局键只有一半时不得凭空生成半套参数，必须当成未配置
     */
    @Test
    @DisplayName("旧版全局键不全时不采用")
    void partialLegacyKeysIgnored() {
        strategyParamService.saveParam(legacyOf("addYield", "5.00"));
        strategyParamService.saveParam(legacyOf("reduceYield", "3.00"));

        DividendPlan plan = resolver.resolveFor(FUND_A, "华泰柏瑞中证红利低波ETF联接C");

        assertThat(plan.complete()).isFalse();
        assertThat(plan.getBaseAmount()).isNull();
        assertThat(plan.getAddYield()).isNull();
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("基准仓位金额未设置"));
        assertThat(plan.getNotes()).anyMatch(text -> text.contains("未配全"));
    }

    /**
     * 单独配置过的基金不会因为存在旧版全局键而回落
     */
    @Test
    @DisplayName("单独配置优先于旧版全局键")
    void customOverridesLegacyGlobal() {
        strategyParamService.saveParam(legacyOf("baseAmount", "100000"));
        resolver.save(planOf(FUND_A, "120000", "70000", "5.20", "3.10"));

        DividendPlan custom = resolver.resolveFor(FUND_A, "华泰柏瑞中证红利低波ETF联接C");

        assertThat(custom.getBaseAmount()).isEqualByComparingTo("120000");
        assertThat(custom.getNotes()).noneMatch(text -> text.contains("沿用旧版"));
    }

    /**
     * 保底高于基准时必须拒绝保存并指出两个字段
     */
    @Test
    @DisplayName("保底高于基准时拒绝保存")
    void floorAboveBaseRejected() {
        List<String> issues = resolver.validate(planOf(FUND_A, "100000", "150000", "5.00", "3.00"));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0)).contains("保底分红金额").contains("不得高于基准仓位");
    }

    /**
     * 减仓阈值不低于加仓阈值时拒绝保存，否则区间不存在
     */
    @Test
    @DisplayName("减仓阈值不低于加仓阈值时拒绝保存")
    void reversedBandRejected() {
        assertThat(resolver.validate(planOf(FUND_A, "100000", "60000", "3.00", "3.00")))
                .anyMatch(text -> text.contains("必须低于加仓阈值"));
        assertThat(resolver.validate(planOf(FUND_A, "100000", "60000", "2.00", "4.00")))
                .anyMatch(text -> text.contains("必须低于加仓阈值"));
    }

    /**
     * 金额为 0 或负数、股息率越界都必须拒绝
     */
    @Test
    @DisplayName("非正金额与越界股息率被拒绝")
    void nonPositiveAndOutOfRangeRejected() {
        assertThat(resolver.validate(planOf(FUND_A, "0", "0", "5.00", "3.00")))
                .anyMatch(text -> text.contains("基准仓位金额必须大于 0"))
                .anyMatch(text -> text.contains("保底分红金额必须大于 0"));
        assertThat(resolver.validate(planOf(FUND_A, "100000", "60000", "0", "3.00")))
                .anyMatch(text -> text.contains("应在 0~100"));
        assertThat(resolver.validate(planOf(FUND_A, "100000", "60000", "150", "3.00")))
                .anyMatch(text -> text.contains("应在 0~100"));
    }

    /**
     * 保存时没有指定标的必须被拒绝，否则会写出无处挂靠的键
     */
    @Test
    @DisplayName("保存必须指定标的")
    void requiresVarietyKey() {
        assertThatThrownBy(() -> resolver.save(planOf(null, "100000", "60000", "5.00", "3.00")))
                .hasMessageContaining("必须指定要配置的标的");
    }

    /**
     * 已配置的基金能被全部列出，供页面逐只编辑
     */
    @Test
    @DisplayName("列出全部已配置的基金")
    void listsConfigured() {
        resolver.save(planOf(FUND_A, "120000", "70000", "5.20", "3.10"));
        resolver.save(planOf(FUND_B, "50000", "20000", "4.50", "2.50"));

        List<DividendPlan> all = resolver.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(DividendPlan::getVarietyKey).containsExactly(FUND_A, FUND_B);
    }

    /**
     * 清除后这只基金回到缺项状态，另一只不受影响
     */
    @Test
    @DisplayName("清除单只不影响另一只")
    void removeOnlyAffectsTarget() {
        resolver.save(planOf(FUND_A, "120000", "70000", "5.20", "3.10"));
        resolver.save(planOf(FUND_B, "50000", "20000", "4.50", "2.50"));

        assertThat(resolver.remove(FUND_A)).isEqualTo(4);

        assertThat(resolver.resolveFor(FUND_A, "A").complete()).isFalse();
        assertThat(resolver.resolveFor(FUND_B, "B").complete()).isTrue();
    }

    /**
     * 库内参数被写成非数值时，读取不抛异常，而是转成缺项清单
     */
    @Test
    @DisplayName("库内非法数值不被静默接受")
    void invalidStoredValueBecomesIssue() {
        ParamSaveRequest bad = new ParamSaveRequest();
        bad.setParamGroup(ParamGroup.DIVIDEND);
        bad.setParamKey(com.lilac.constant.DividendParamKey.keyOf(FUND_A, "baseAmount"));
        bad.setParamValue("十万");
        strategyParamService.saveParam(bad);
        resolver.save(planOf(FUND_A, "100000", "60000", "5.00", "3.00"));
        strategyParamService.saveParam(bad);

        DividendPlan plan = resolver.resolveFor(FUND_A, "A");

        assertThat(plan.complete()).isFalse();
        assertThat(plan.getIssues()).anyMatch(text -> text.contains("基准仓位金额"));
    }

    /**
     * 构造旧版全局键的保存请求
     *
     * @param key   参数名
     * @param value 参数值
     * @return 保存请求
     */
    private ParamSaveRequest legacyOf(String key, String value) {
        ParamSaveRequest request = new ParamSaveRequest();
        request.setParamGroup(ParamGroup.DIVIDEND);
        request.setParamKey(key);
        request.setParamValue(value);
        return request;
    }

    /**
     * 构造参数
     *
     * @param varietyKey  品种业务主键
     * @param base        基准仓位金额
     * @param floor       保底分红金额
     * @param addYield    加仓阈值
     * @param reduceYield 减仓阈值
     * @return 参数
     */
    private DividendPlan planOf(String varietyKey, String base, String floor, String addYield,
                                String reduceYield) {
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey(varietyKey);
        plan.setBaseAmount(new BigDecimal(base));
        plan.setFloorAmount(new BigDecimal(floor));
        plan.setAddYield(new BigDecimal(addYield));
        plan.setReduceYield(new BigDecimal(reduceYield));
        return plan;
    }
}
