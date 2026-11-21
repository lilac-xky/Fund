package com.lilac.strategy;

import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.FundNetWorth;
import com.lilac.domain.entity.Holding;
import com.lilac.domain.vo.DividendAdviceVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.DividendPlanVO;
import com.lilac.enums.DividendAction;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.FundNetWorthMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.mapper.IndexDividendMapper;
import com.lilac.mapper.StrategyParamMapper;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.IndexDividendService;
import com.lilac.service.VarietyService;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红利建议验收测试，股息率走手工录入，数据库走真实实例
 */
@SpringBootTest
@Transactional
class DividendAdviceIntegrationTest {

    private static final String DIVIDEND_CODE = "990922";
    private static final String DIVIDEND_KEY = "OF:" + DIVIDEND_CODE;
    private static final String INDEX_CODE = "000922";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private DividendAdviceService dividendAdviceService;

    @Autowired
    private DividendPlanResolver dividendPlanResolver;

    @Autowired
    private IndexDividendService indexDividendService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private HoldingMapper holdingMapper;

    @Autowired
    private FundNetWorthMapper fundNetWorthMapper;

    @Autowired
    private StrategyParamMapper strategyParamMapper;

    @Autowired
    private IndexDividendMapper indexDividendMapper;

    /**
     * 清掉本组用例会用到的数据，避免跨用例串数据
     */
    @BeforeEach
    void setUp() {
        holdingMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        fundNetWorthMapper.deleteByQuery(QueryWrapper.create().eq("fundCode", DIVIDEND_CODE));
        strategyParamMapper.deleteByQuery(QueryWrapper.create().eq("paramGroup", "DIVIDEND"));
        indexDividendMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        varietyService.saveVariety(dividendVariety());
    }

    /**
     * 监控对象说明这套参数管的是哪只基金：带出跟踪指数、股息率与数据日期
     */
    @Test
    @DisplayName("监控对象带出跟踪指数与股息率")
    void watchListExplainsTarget() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "4.23"));
        saveHolding("50000.0000");
        saveNetWorth("1.5000");

        var watch = dividendAdviceService.watchList();

        assertThat(watch).hasSize(1);
        var row = watch.get(0);
        assertThat(row.getVarietyKey()).isEqualTo(DIVIDEND_KEY);
        assertThat(row.getIndexCode()).isEqualTo(INDEX_CODE);
        assertThat(row.getDividendYield()).isEqualByComparingTo("4.23");
        assertThat(row.getDataDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(row.getCurrentAmount()).isEqualByComparingTo("75000");
        assertThat(row.getUsable()).isTrue();
        assertThat(row.getReasons()).isEmpty();
    }

    /**
     * 未登记跟踪指数时无法取股息率，监控对象要说明原因而不是静默不参与
     */
    @Test
    @DisplayName("未登记跟踪指数时说明原因")
    void watchListExplainsMissingIndex() {
        savePlan();
        // 另建一只从未登记过跟踪指数的红利基金，避免去改已有行的 null 值
        String noIndexCode = "990923";
        String noIndexKey = "OF:" + noIndexCode;
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey(noIndexKey);
        request.setVarietyType(VarietyType.OF);
        request.setVarietyCode(noIndexCode);
        request.setVarietyName("未登记指数的红利基金");
        request.setCategory(FundCategory.DIVIDEND);
        request.setConfirmed(Boolean.TRUE);
        varietyService.saveVariety(request);
        saveHoldingOf(noIndexKey, "50000.0000");

        var watch = dividendAdviceService.watchList();

        assertThat(watch).hasSize(1);
        assertThat(watch.get(0).getVarietyKey()).isEqualTo(noIndexKey);
        assertThat(watch.get(0).getUsable()).isFalse();
        assertThat(watch.get(0).getReasons())
                .anyMatch(text -> text.contains("未登记跟踪指数"));
        // 缺指数时不该再去说「该指数尚无股息率」，两条原因会互相矛盾
        assertThat(watch.get(0).getReasons())
                .noneMatch(text -> text.contains("该指数尚无股息率数据"));
    }

    /**
     * 股息率过期时监控对象标为过期并带出原因
     */
    @Test
    @DisplayName("股息率过期时监控对象标注原因")
    void watchListMarksStale() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(30), "4.23"));
        saveHolding("50000.0000");
        saveNetWorth("1.5000");

        var watch = dividendAdviceService.watchList();

        assertThat(watch.get(0).getStale()).isTrue();
        assertThat(watch.get(0).getUsable()).isFalse();
        assertThat(watch.get(0).getReasons()).anyMatch(text -> text.contains("不得据此给出加减仓动作"));
    }

    /**
     * 按份额乘净值推算的当前金额与手工核算一致，金额精确到元
     */
    @Test
    @DisplayName("按份额乘净值推算当前金额")
    void producesYuanPreciseAmount() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "6.20"));
        saveHolding("50000.5000");
        saveNetWorth("2.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        // 50000.5 × 2.0000 = 100001.00，高于 6.20% 档的 10 万，按阶梯要减回该档
        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("100001");
        assertThat(advice.getAction()).isEqualTo(DividendAction.REDUCE);
        assertThat(advice.getSuggestAmount()).isEqualByComparingTo("1");
        assertThat(advice.getRemainingAmount()).isEqualByComparingTo("100000");
    }

    /**
     * 构造减仓场景，建议减仓金额与剩余金额都精确到元
     */
    @Test
    @DisplayName("减仓建议金额与剩余金额精确到元")
    void reduceAmountIsYuanPrecise() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY, "2.30"));
        saveHolding("4000.0000");
        saveNetWorth("25.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        assertThat(advice.getAction()).isEqualTo(DividendAction.REDUCE);
        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("100000");
        assertThat(advice.getSuggestAmount()).isEqualByComparingTo("40000");
        assertThat(advice.getSuggestAmount().scale()).isZero();
        assertThat(advice.getRemainingAmount()).isEqualByComparingTo("60000");
        assertThat(advice.getRemainingAmount()).isGreaterThanOrEqualTo(new BigDecimal("60000"));
        assertThat(advice.getGridSuppressed()).isTrue();
        assertThat(advice.getGridNote()).contains("优先于网格提醒");
    }

    /**
     * 股息率跌破阶梯最低档时按住保底档：不足则补到保底，多于此则减回保底，不会清仓
     */
    @Test
    @DisplayName("跌破最低档时按住保底金额")
    void belowBottomRungHoldsFloor() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY, "1.80"));
        saveHolding("5000.0000");
        saveNetWorth("11.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        // 5000 × 11 = 55000，低于保底档 6 万，补到 6 万
        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("55000");
        assertThat(advice.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(advice.getSuggestAmount()).isEqualByComparingTo("5000");
        assertThat(advice.getRemainingAmount()).isEqualByComparingTo("60000");
        assertThat(advice.getRemarks()).anyMatch(text -> text.contains("按最低一档的保底金额"));
    }

    /**
     * 股息率数据过期时不给出动作，但数据日期与过期原因必须在出参里
     */
    @Test
    @DisplayName("股息率过期时不给动作且标注数据日期")
    void staleDividendYieldsNoAction() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(20), "6.20"));
        saveHolding("1000.0000");
        saveNetWorth("50.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        assertThat(advice.getAction()).isEqualTo(DividendAction.UNDECIDABLE);
        assertThat(advice.getSuggestAmount()).isNull();
        assertThat(advice.getDataDate()).isEqualTo(TODAY.minusDays(20));
        assertThat(advice.getStale()).isTrue();
        assertThat(advice.getAgeDays()).isEqualTo(20);
        assertThat(advice.getRemarks()).anyMatch(text -> text.contains("超过 7 天阈值"));
    }

    /**
     * 金额不随股息率递增时拒绝保存，否则越跌越买会变成越跌越卖
     */
    @Test
    @DisplayName("金额不随股息率递增时拒绝保存")
    void nonMonotonicAmountRejected() {
        DividendPlan plan = planOf(rungOf("6.00", "30000"), rungOf("5.00", "60000"));

        assertThatThrownBy(() -> saveDirectly(plan))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("股息率越高应持有越多");
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", "DIVIDEND"))).isZero();
    }

    /**
     * 只有一档时拒绝保存，因为表达不了金字塔
     */
    @Test
    @DisplayName("只有一档时拒绝保存")
    void singleRungRejected() {
        DividendPlan plan = planOf(rungOf("5.00", "60000"));

        assertThatThrownBy(() -> saveDirectly(plan))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要 2 档");
    }

    /**
     * 参数未设置时整批不给动作，并返回缺项清单
     */
    @Test
    @DisplayName("参数未设置时不给出任何动作")
    void missingPlanBlocksAll() {
        saveHolding("4000.0000");
        saveNetWorth("25.0000");

        DividendAdvisoryVO result = dividendAdviceService.adviseAll();

        assertThat(result.getParamUsable()).isFalse();
        assertThat(result.getParamIssues()).hasSize(1);
        assertThat(result.getActionCount()).isZero();
        assertThat(result.getUndecidableCount()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getSkippedItems().get(0).getAction()).isEqualTo(DividendAction.UNDECIDABLE);
    }

    /**
     * 批量建议只纳入持有中的红利基金，非红利与无持仓的都不出建议
     */
    @Test
    @DisplayName("批量建议只纳入持有中的红利基金")
    void batchCoversOnlyHeldDividendFunds() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "2.20"));
        saveHolding("4000.0000");
        saveNetWorth("25.0000");
        // 非红利品种与无持仓的红利品种都不应出现在建议里
        varietyService.saveVariety(stockVariety());
        saveHoldingOf("OF:880001", "1000.0000");

        DividendAdvisoryVO result = dividendAdviceService.adviseAll();

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getVarietyKey()).isEqualTo(DIVIDEND_KEY);
        assertThat(result.getTotalCurrentAmount()).isEqualByComparingTo("100000");
    }

    /**
     * 无红利持仓时给出可读提示，不返回空白结果
     */
    @Test
    @DisplayName("无红利持仓时给出可读提示")
    void noHoldingGivesNotice() {
        savePlan();

        DividendAdvisoryVO result = dividendAdviceService.adviseAll();

        assertThat(result.getRequested()).isZero();
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNotice()).contains("尚无持有中的红利基金");
    }

    /**
     * 非红利品种不接受红利建议，返回具体原因
     */
    @Test
    @DisplayName("非红利品种不接受红利建议")
    void nonDividendRejected() {
        savePlan();
        varietyService.saveVariety(stockVariety());

        DividendAdviceRequest request = new DividendAdviceRequest();
        request.setVarietyKey("OF:880001");
        assertThatThrownBy(() -> dividendAdviceService.adviseOne(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("红利建议只适用于红利基金");
    }

    /**
     * 参数保存后可读回，且 usable 为 true
     */
    @Test
    @DisplayName("参数保存后可读回")
    void planRoundTrips() {
        savePlan();

        DividendPlanVO plan = dividendAdviceService.getPlan(DIVIDEND_KEY);

        assertThat(plan.getUsable()).isTrue();
        assertThat(plan.getIssues()).isEmpty();
        assertThat(plan.getVarietyKey()).isEqualTo(DIVIDEND_KEY);
        assertThat(plan.getLadder()).hasSize(3);
        assertThat(plan.getTopAmount()).isEqualByComparingTo("100000");
        assertThat(plan.getBottomAmount()).isEqualByComparingTo("60000");
        assertThat(plan.getLadder().get(0).getYield()).isEqualByComparingTo("6.20");
    }

    /**
     * 手工指定当前金额时以手工值为准，并标注来源
     */
    @Test
    @DisplayName("手工指定当前金额时以手工值为准")
    void manualAmountOverridesHolding() {
        savePlan();
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "6.20"));

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(new BigDecimal("40000")));

        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("40000");
        assertThat(advice.getAmountSource()).contains("手工指定");
        assertThat(advice.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(advice.getSuggestAmount()).isEqualByComparingTo("60000");
    }

    /**
     * 保存一条三档阶梯：6.20% 补到 10 万、5.00% 补到 8 万、3.00% 保底 6 万
     */
    private void savePlan() {
        dividendPlanResolver.save(defaultLadder());
    }

    /**
     * 构造默认阶梯
     *
     * @return 参数
     */
    private DividendPlan defaultLadder() {
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey(DIVIDEND_KEY);
        plan.setLadder(new ArrayList<>(List.of(
                rungOf("6.20", "100000"),
                rungOf("5.00", "80000"),
                rungOf("3.00", "60000"))));
        return plan;
    }

    /**
     * 构造一档
     *
     * @param yield  股息率门槛
     * @param amount 应持有金额
     * @return 档位
     */
    private DividendLadderRung rungOf(String yield, String amount) {
        DividendLadderRung rung = new DividendLadderRung();
        rung.setYield(new BigDecimal(yield));
        rung.setAmount(new BigDecimal(amount));
        return rung;
    }

    /**
     * 直接走参数装配器保存，用于验证校验拒绝路径
     *
     * @param plan 参数
     */
    private void saveDirectly(DividendPlan plan) {
        dividendPlanResolver.save(plan);
    }

    /**
     * 构造一条指定阶梯的参数，品种固定为本组用例的红利基金
     *
     * @param rungs 阶梯
     * @return 参数
     */
    private DividendPlan planOf(DividendLadderRung... rungs) {
        DividendPlan plan = new DividendPlan();
        plan.setVarietyKey(DIVIDEND_KEY);
        plan.setLadder(new ArrayList<>(List.of(rungs)));
        return plan;
    }

    /**
     * 构造建议请求
     *
     * @param currentAmount 手工指定的当前金额，可为 null
     * @return 建议请求
     */
    private DividendAdviceRequest requestOf(BigDecimal currentAmount) {
        DividendAdviceRequest request = new DividendAdviceRequest();
        request.setVarietyKey(DIVIDEND_KEY);
        request.setCurrentAmount(currentAmount);
        return request;
    }

    /**
     * 构造手工录入股息率请求
     *
     * @param dataDate 数据日期
     * @param yield    股息率
     * @return 手工录入请求
     */
    private DividendManualRequest manualOf(LocalDate dataDate, String yield) {
        DividendManualRequest request = new DividendManualRequest();
        request.setIndexCode(INDEX_CODE);
        request.setDataDate(dataDate);
        request.setDividendYield(new BigDecimal(yield));
        return request;
    }

    /**
     * 写入一只持有中的红利持仓
     *
     * @param shares 持有份额
     */
    private void saveHolding(String shares) {
        saveHoldingOf(DIVIDEND_KEY, shares);
    }

    /**
     * 为指定品种写入持有中的持仓
     *
     * @param varietyKey 品种业务主键
     * @param shares     持有份额
     */
    private void saveHoldingOf(String varietyKey, String shares) {
        Holding holding = new Holding();
        holding.setVarietyKey(varietyKey);
        holding.setShares(new BigDecimal(shares));
        holding.setCostPrice(new BigDecimal("1.0000"));
        holding.setOpenDate(TODAY.minusDays(30));
        holding.setStatus(HoldingStatus.HOLDING);
        holdingMapper.insertSelective(holding);
    }

    /**
     * 写入一条最新净值
     *
     * @param netWorth 单位净值
     */
    private void saveNetWorth(String netWorth) {
        FundNetWorth row = new FundNetWorth();
        row.setFundCode(DIVIDEND_CODE);
        row.setNetWorthDate(TODAY.minusDays(1));
        row.setUnitNetWorth(new BigDecimal(netWorth));
        row.setEquityReturn(BigDecimal.ZERO);
        fundNetWorthMapper.insertSelective(row);
    }

    /**
     * 构造红利品种保存请求
     *
     * @return 保存请求
     */
    private VarietySaveRequest dividendVariety() {
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey(DIVIDEND_KEY);
        request.setVarietyType(VarietyType.OF);
        request.setVarietyCode(DIVIDEND_CODE);
        request.setVarietyName("测试红利指数基金A");
        request.setCategory(FundCategory.DIVIDEND);
        request.setTrackIndexCode(INDEX_CODE);
        request.setConfirmed(Boolean.TRUE);
        return request;
    }

    /**
     * 构造非红利的股票型品种保存请求
     *
     * @return 保存请求
     */
    private VarietySaveRequest stockVariety() {
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey("OF:880001");
        request.setVarietyType(VarietyType.OF);
        request.setVarietyCode("880001");
        request.setVarietyName("测试股票型基金");
        request.setCategory(FundCategory.STOCK);
        request.setConfirmed(Boolean.TRUE);
        return request;
    }
}
