package com.lilac.strategy;

import com.lilac.domain.dto.dividend.DividendAdviceRequest;
import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.dividend.DividendParamSaveRequest;
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
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
     * 按份额乘净值推算的当前金额与手工核算一致，金额精确到元
     */
    @Test
    @DisplayName("按份额乘净值推算当前金额")
    void producesYuanPreciseAmount() {
        savePlan("100000", "60000", "5.00", "3.00");
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "6.20"));
        saveHolding("50000.5000");
        saveNetWorth("2.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        // 50000.5 × 2.0000 = 100001.00 已超基准，股息率再高也不追
        assertThat(advice.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("100001");
        assertThat(advice.getSuggestAmount()).isNull();
        assertThat(advice.getRemarks()).anyMatch(text -> text.contains("不追高"));
    }

    /**
     * 构造减仓场景，建议减仓金额与剩余金额都精确到元
     */
    @Test
    @DisplayName("减仓建议金额与剩余金额精确到元")
    void reduceAmountIsYuanPrecise() {
        savePlan("100000", "60000", "5.00", "3.00");
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
     * 减仓会击穿保底分红金额时输出持有，并说明是保底拦下
     */
    @Test
    @DisplayName("减仓会击穿保底金额时输出持有")
    void holdsWhenFloorBreaks() {
        savePlan("100000", "60000", "5.00", "3.00");
        indexDividendService.saveManual(manualOf(TODAY, "1.80"));
        saveHolding("5000.0000");
        saveNetWorth("11.0000");

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(null));

        assertThat(advice.getAction()).isEqualTo(DividendAction.HOLD);
        assertThat(advice.getSuggestAmount()).isNull();
        assertThat(advice.getFloorProtected()).isTrue();
        assertThat(advice.getRemarks()).anyMatch(text -> text.contains("减仓会击穿保底"));
    }

    /**
     * 股息率数据过期时不给出动作，但数据日期与过期原因必须在出参里
     */
    @Test
    @DisplayName("股息率过期时不给动作且标注数据日期")
    void staleDividendYieldsNoAction() {
        savePlan("100000", "60000", "5.00", "3.00");
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
     * 保底高于基准时拒绝保存并指出字段，不静默接受
     */
    @Test
    @DisplayName("保底高于基准时拒绝保存并指出字段")
    void floorAboveBaseRejected() {
        DividendParamSaveRequest request = planRequestOf("100000", "150000", "5.00", "3.00");

        assertThatThrownBy(() -> dividendAdviceService.savePlan(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保底分红金额")
                .hasMessageContaining("不得高于基准仓位");
        assertThat(strategyParamMapper.selectCountByQuery(QueryWrapper.create()
                .eq("paramGroup", "DIVIDEND"))).isZero();
    }

    /**
     * 阈值顺序颠倒时拒绝保存，减仓阈值必须低于加仓阈值
     */
    @Test
    @DisplayName("阈值顺序颠倒时拒绝保存")
    void reversedBandRejected() {
        DividendParamSaveRequest request = planRequestOf("100000", "60000", "3.00", "5.00");

        assertThatThrownBy(() -> dividendAdviceService.savePlan(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须低于加仓阈值");
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
        assertThat(result.getParamIssues()).hasSize(4);
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
        savePlan("100000", "60000", "5.00", "3.00");
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
        savePlan("100000", "60000", "5.00", "3.00");

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
        savePlan("100000", "60000", "5.00", "3.00");
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
        dividendAdviceService.savePlan(planRequestOf("120000", "70000", "5.20", "3.10"));

        DividendPlanVO plan = dividendAdviceService.getPlan();

        assertThat(plan.getUsable()).isTrue();
        assertThat(plan.getIssues()).isEmpty();
        assertThat(plan.getBaseAmount()).isEqualByComparingTo("120000");
        assertThat(plan.getFloorAmount()).isEqualByComparingTo("70000");
        assertThat(plan.getAddYield()).isEqualByComparingTo("5.20");
        assertThat(plan.getReduceYield()).isEqualByComparingTo("3.10");
    }

    /**
     * 手工指定当前金额时以手工值为准，并标注来源
     */
    @Test
    @DisplayName("手工指定当前金额时以手工值为准")
    void manualAmountOverridesHolding() {
        savePlan("100000", "60000", "5.00", "3.00");
        indexDividendService.saveManual(manualOf(TODAY.minusDays(1), "6.20"));

        DividendAdviceVO advice = dividendAdviceService.adviseOne(requestOf(new BigDecimal("40000")));

        assertThat(advice.getCurrentAmount()).isEqualByComparingTo("40000");
        assertThat(advice.getAmountSource()).contains("手工指定");
        assertThat(advice.getAction()).isEqualTo(DividendAction.ADD);
        assertThat(advice.getSuggestAmount()).isEqualByComparingTo("60000");
    }

    /**
     * 保存红利参数
     *
     * @param base        基准仓位金额
     * @param floor       保底分红金额
     * @param addYield    加仓阈值
     * @param reduceYield 减仓阈值
     */
    private void savePlan(String base, String floor, String addYield, String reduceYield) {
        dividendAdviceService.savePlan(planRequestOf(base, floor, addYield, reduceYield));
    }

    /**
     * 构造参数保存请求
     *
     * @param base        基准仓位金额
     * @param floor       保底分红金额
     * @param addYield    加仓阈值
     * @param reduceYield 减仓阈值
     * @return 保存请求
     */
    private DividendParamSaveRequest planRequestOf(String base, String floor, String addYield, String reduceYield) {
        DividendParamSaveRequest request = new DividendParamSaveRequest();
        request.setBaseAmount(new BigDecimal(base));
        request.setFloorAmount(new BigDecimal(floor));
        request.setAddYield(new BigDecimal(addYield));
        request.setReduceYield(new BigDecimal(reduceYield));
        return request;
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
