package com.lilac.strategy;

import com.lilac.constant.DividendParamKey;
import com.lilac.domain.dto.param.ParamSaveRequest;
import com.lilac.enums.ParamGroup;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.StrategyParamService;
import com.lilac.strategy.model.DividendLadderRung;
import com.lilac.strategy.model.DividendPlan;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红利阶梯按基金存储与校验测试，核心是「两只基金互不覆盖」与「阶梯必须单调」
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
     * 两只基金各存一条阶梯，读回时互不覆盖
     */
    @Test
    @DisplayName("按基金各存一套阶梯且互不覆盖")
    void storesPerVariety() {
        resolver.save(planOf(FUND_A, rung("6.00", "100000"), rung("5.00", "60000"), rung("4.50", "30000")));
        resolver.save(planOf(FUND_B, rung("5.00", "20000"), rung("4.50", "10000")));

        DividendPlan a = resolver.resolveFor(FUND_A, "华泰柏瑞中证红利低波ETF联接C");
        DividendPlan b = resolver.resolveFor(FUND_B, "南方标普红利低波50ETF联接C");

        assertThat(a.complete()).isTrue();
        assertThat(a.getLadder()).hasSize(3);
        assertThat(a.topAmount()).isEqualByComparingTo("100000");
        assertThat(a.bottomAmount()).isEqualByComparingTo("30000");
        assertThat(b.getLadder()).hasSize(2);
        assertThat(b.topAmount()).isEqualByComparingTo("20000");
        // 每只一行，键上带品种前缀
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()))).isEqualTo(2);
    }

    /**
     * 没配过的基金返回空阶梯与缺项清单，不拿别的基金顶上
     */
    @Test
    @DisplayName("没配过的基金返回缺项清单")
    void unconfiguredFundHasNoLadder() {
        resolver.save(planOf(FUND_A, rung("6.00", "100000"), rung("5.00", "60000")));

        DividendPlan other = resolver.resolveFor("OF:999999", "另一只红利基金");

        assertThat(other.complete()).isFalse();
        assertThat(other.getLadder()).isEmpty();
        assertThat(other.getIssues()).anyMatch(text -> text.contains("股息率阶梯未设置"));
    }

    /**
     * 阶梯乱序保存后按股息率降序读回，页面与计算器都不用自己排
     */
    @Test
    @DisplayName("阶梯按股息率降序归一")
    void ladderIsSortedDescending() {
        resolver.save(planOf(FUND_A, rung("4.50", "30000"), rung("6.00", "100000"), rung("5.00", "60000")));

        DividendPlan plan = resolver.resolveFor(FUND_A, "A");

        assertThat(plan.getLadder()).hasSize(3);
        // BigDecimal 的 equals 对小数位敏感，逐档按数值比较
        assertThat(plan.getLadder().get(0).getYield()).isEqualByComparingTo("6.00");
        assertThat(plan.getLadder().get(1).getYield()).isEqualByComparingTo("5.00");
        assertThat(plan.getLadder().get(2).getYield()).isEqualByComparingTo("4.50");
    }

    /**
     * 只有一档时拒绝保存，因为表达不了金字塔
     */
    @Test
    @DisplayName("只有一档时拒绝保存")
    void singleRungRejected() {
        assertThat(resolver.validate(planOf(FUND_A, rung("5.00", "60000"))))
                .anyMatch(text -> text.contains("至少需要 2 档"));
    }

    /**
     * 股息率越高、应持有金额反而更少时必须拒绝，否则越跌越买变成越跌越卖
     */
    @Test
    @DisplayName("金额不随股息率递增时拒绝保存")
    void nonMonotonicAmountRejected() {
        // 6.00% 只配 3 万，5.00% 反而配 6 万，方向反了
        List<String> issues = resolver.validate(planOf(FUND_A, rung("6.00", "30000"), rung("5.00", "60000")));

        assertThat(issues).anyMatch(text -> text.contains("股息率越高应持有越多"));
    }

    /**
     * 重复的股息率门槛必须拒绝，否则同一门槛命中哪一档不确定
     */
    @Test
    @DisplayName("重复门槛拒绝保存")
    void duplicateYieldRejected() {
        assertThat(resolver.validate(planOf(FUND_A, rung("5.00", "60000"), rung("5.00", "30000"))))
                .anyMatch(text -> text.contains("重复的股息率"));
    }

    /**
     * 非正金额与越界股息率必须拒绝
     */
    @Test
    @DisplayName("非正金额与越界股息率被拒绝")
    void nonPositiveAndOutOfRangeRejected() {
        assertThat(resolver.validate(planOf(FUND_A, rung("6.00", "0"), rung("5.00", "60000"))))
                .anyMatch(text -> text.contains("必须大于 0"));
        assertThat(resolver.validate(planOf(FUND_A, rung("150", "60000"), rung("5.00", "30000"))))
                .anyMatch(text -> text.contains("应在 0~100"));
    }

    /**
     * 保存时没有指定标的必须被拒绝
     */
    @Test
    @DisplayName("保存必须指定标的")
    void requiresVarietyKey() {
        assertThatThrownBy(() -> resolver.save(planOf(null, rung("6.00", "100000"), rung("5.00", "60000"))))
                .hasMessageContaining("必须指定要配置的标的");
    }

    /**
     * 未通过校验时一条都不落库
     */
    @Test
    @DisplayName("校验失败时不落库")
    void invalidPlanNotPersisted() {
        assertThatThrownBy(() -> resolver.save(planOf(FUND_A, rung("6.00", "30000"), rung("5.00", "60000"))))
                .hasMessageContaining("股息率越高应持有越多");
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", ParamGroup.DIVIDEND.name()))).isZero();
    }

    /**
     * 已配置的基金能被全部列出
     */
    @Test
    @DisplayName("列出全部已配置的基金")
    void listsConfigured() {
        resolver.save(planOf(FUND_A, rung("6.00", "100000"), rung("5.00", "60000")));
        resolver.save(planOf(FUND_B, rung("5.00", "20000"), rung("4.50", "10000")));

        List<DividendPlan> all = resolver.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(DividendPlan::getVarietyKey).containsExactly(FUND_A, FUND_B);
    }

    /**
     * 清除后这只基金回到未配置，另一只不受影响
     */
    @Test
    @DisplayName("清除单只不影响另一只")
    void removeOnlyAffectsTarget() {
        resolver.save(planOf(FUND_A, rung("6.00", "100000"), rung("5.00", "60000")));
        resolver.save(planOf(FUND_B, rung("5.00", "20000"), rung("4.50", "10000")));

        assertThat(resolver.remove(FUND_A)).isTrue();

        assertThat(resolver.resolveFor(FUND_A, "A").complete()).isFalse();
        assertThat(resolver.resolveFor(FUND_B, "B").complete()).isTrue();
    }

    /**
     * 旧版单阈值键仍留在库里时给出提示，但不影响按阶梯的工作
     */
    @Test
    @DisplayName("旧版单阈值键给出迁移提示")
    void legacyKeysProduceNote() {
        strategyParamService.saveParam(legacyOf("baseAmount", "100000"));
        strategyParamService.saveParam(legacyOf("floorAmount", "60000"));

        DividendPlan plan = resolver.resolveFor(FUND_A, "A");

        assertThat(plan.getLadder()).isEmpty();
        assertThat(plan.getNotes()).anyMatch(text -> text.contains("旧版的单阈值方式"));
        // 旧键不参与判定，因此仍然算未配置
        assertThat(plan.complete()).isFalse();
    }

    /**
     * 库里是脏 JSON 时按未配置处理，不抛异常打断整页读取
     */
    @Test
    @DisplayName("脏 JSON 不被静默接受也不炸")
    void brokenJsonTreatedAsMissing() {
        ParamSaveRequest bad = new ParamSaveRequest();
        bad.setParamGroup(ParamGroup.DIVIDEND);
        bad.setParamKey(DividendParamKey.keyOf(FUND_A));
        bad.setParamValue("这不是 JSON");
        strategyParamService.saveParam(bad);

        DividendPlan plan = resolver.resolveFor(FUND_A, "A");

        assertThat(plan.getLadder()).isEmpty();
        assertThat(plan.complete()).isFalse();
    }

    /**
     * 构造旧版单阈值键
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
     * @param varietyKey 品种业务主键
     * @param rungs      阶梯
     * @return 参数
     */
    private DividendPlan planOf(String varietyKey, DividendLadderRung... rungs) {
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey(varietyKey);
        plan.setLadder(new ArrayList<>(List.of(rungs)));
        return plan;
    }
}
