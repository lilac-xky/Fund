package com.lilac.mail;

import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.BondAdvisoryVO;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.IndicatorAdvisoryVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.VarietyType;
import com.lilac.service.BondMonitorService;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.GridAdviceService;
import com.lilac.service.HoldingService;
import com.lilac.service.IndicatorService;
import com.lilac.service.IndexDividendService;
import com.lilac.service.VarietyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 邮件内容装配测试，重点是无可预估数字、无持仓与红利段降级三种边界
 */
class MailContentBuilderTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 30);

    private HoldingService holdingService;
    private VarietyService varietyService;
    private DividendAdviceService dividendAdviceService;
    private IndexDividendService indexDividendService;
    private GridAdviceService gridAdviceService;
    private BondMonitorService bondMonitorService;
    private IndicatorService indicatorService;
    private MailContentBuilder builder;

    /**
     * 七类业务全部用桩，装配逻辑单独验证
     */
    @BeforeEach
    void setUp() {
        holdingService = mock(HoldingService.class);
        varietyService = mock(VarietyService.class);
        dividendAdviceService = mock(DividendAdviceService.class);
        indexDividendService = mock(IndexDividendService.class);
        gridAdviceService = mock(GridAdviceService.class);
        bondMonitorService = mock(BondMonitorService.class);
        indicatorService = mock(IndicatorService.class);
        builder = new MailContentBuilder(holdingService, varietyService, dividendAdviceService,
                indexDividendService, gridAdviceService, bondMonitorService, indicatorService);
    }

    /**
     * 尚无持仓时模型标记为空，正文改发提示邮件
     */
    @Test
    @DisplayName("无持仓时模型为空")
    void emptyWhenNoHolding() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of());
        MailContent content = builder.build(TRADE_DATE, null);

        assertThat(content.empty()).isTrue();
        assertThat(content.getVarietyCount()).isZero();
    }

    /**
     * 预估缺失时把持仓全部归入不可预估并带原因，绝不给数字
     */
    @Test
    @DisplayName("预估缺失时全部归入不可预估")
    void estimateMissingGoesUnavailable() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:000922")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(dividend()));
        when(dividendAdviceService.adviseAll()).thenReturn(emptyAdvisory());

        MailContent content = builder.build(TRADE_DATE, null);
        assertThat(content.getItems()).isEmpty();
        assertThat(content.getUnavailableItems()).hasSize(1);
        assertThat(content.getUnavailableItems().get(0).getUnavailableReason()).contains("未产出");
        assertThat(content.getOverallChange()).isNull();
    }

    /**
     * 红利建议装配抛异常时不拖垮整封邮件，只把红利段标为不可用
     */
    @Test
    @DisplayName("红利建议失败时邮件仍可发送")
    void dividendFailureDegrades() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:000922")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(dividend()));
        when(dividendAdviceService.adviseAll()).thenThrow(new IllegalStateException("参数表读取失败"));

        MailContent content = builder.build(TRADE_DATE, estimateWithOne());
        assertThat(content.isDividendAvailable()).isFalse();
        assertThat(content.getDividendSummary()).anySatisfy(item -> assertThat(item).contains("失败"));
        assertThat(content.getItems()).hasSize(1);
    }

    /**
     * 债券持仓存在时邮件必须带债券段，触发提醒时摘要里要有具体数值
     */
    @Test
    @DisplayName("债券触发时邮件带出换仓提醒")
    void bondAlertReachesMail() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:006327")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(bond()));
        when(bondMonitorService.adviseAll()).thenReturn(bondAdvisory(true));

        MailContent content = builder.build(TRADE_DATE, estimateWithOne());
        assertThat(content.getBondSummary()).isNotEmpty();
        assertThat(content.getBondSummary()).anySatisfy(item -> assertThat(item).contains("横盘"));
        assertThat(content.isBondAvailable()).isTrue();
    }

    /**
     * 未达阈值时债券段不给换仓动作，避免正常情形误报
     */
    @Test
    @DisplayName("债券未达阈值时不产生提醒")
    void bondNormalNoAlert() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:006327")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(bond()));
        when(bondMonitorService.adviseAll()).thenReturn(bondAdvisory(false));

        MailContent content = builder.build(TRADE_DATE, estimateWithOne());
        assertThat(content.getBondSummary()).anySatisfy(item -> assertThat(item).contains("未达换仓阈值"));
    }

    /**
     * 指标开关关闭时邮件里不得出现指标信号，这是参数页对使用者的承诺
     */
    @Test
    @DisplayName("关闭参与提醒后邮件不给指标信号")
    void indicatorSwitchOff() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:000922")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(dividend()));
        when(dividendAdviceService.adviseAll()).thenReturn(emptyAdvisory());
        IndicatorAdvisoryVO advisory = new IndicatorAdvisoryVO();
        advisory.setParamUsable(Boolean.TRUE);
        advisory.setParticipateInAlert(Boolean.FALSE);
        advisory.setSummary(List.of("000922 RSI 超买"));
        when(indicatorService.computeAll()).thenReturn(advisory);

        MailContent content = builder.build(TRADE_DATE, estimateWithOne());
        assertThat(content.isIndicatorAvailable()).isFalse();
        assertThat(content.getIndicatorSummary()).anySatisfy(item -> assertThat(item).contains("参与提醒"));
    }

    /**
     * 指标有信号时进邮件，且摘要里带指标数值
     */
    @Test
    @DisplayName("指标信号进邮件并带数值")
    void indicatorSignalReachesMail() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class))).thenReturn(List.of(holding("OF:000922")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(dividend()));
        when(dividendAdviceService.adviseAll()).thenReturn(emptyAdvisory());
        IndicatorAdvisoryVO advisory = new IndicatorAdvisoryVO();
        advisory.setParamUsable(Boolean.TRUE);
        advisory.setParticipateInAlert(Boolean.TRUE);
        advisory.setSummary(List.of("000922 中证红利ETF联接A：RSI 超买，RSI 78.50"));
        when(indicatorService.computeAll()).thenReturn(advisory);

        MailContent content = builder.build(TRADE_DATE, estimateWithOne());
        assertThat(content.isIndicatorAvailable()).isTrue();
        assertThat(content.getIndicatorSummary()).anySatisfy(item -> assertThat(item).contains("RSI 超买"));
    }

    /**
     * 可预估与不可预估分两段，且数据截止时间取行情日期
     */
    @Test
    @DisplayName("可预估与不可预估分两段且带数据截止时间")
    void splitsEstimableAndUnavailable() {
        when(holdingService.listHolding(any(HoldingQueryRequest.class)))
                .thenReturn(List.of(holding("OF:000922"), holding("OF:005827")));
        when(varietyService.listVariety(any(VarietyQueryRequest.class)))
                .thenReturn(List.of(dividend(), mixed()));
        when(dividendAdviceService.adviseAll()).thenReturn(emptyAdvisory());

        EstimateBatchVO estimate = estimateWithOne();
        EstimateVO gap = new EstimateVO();
        gap.setVarietyKey("OF:005827");
        gap.setVarietyCode("005827");
        gap.setVarietyName("易方达蓝筹精选");
        gap.setEstimable(false);
        gap.setUnavailableReason("重仓股停牌");
        estimate.setUnavailableItems(List.of(gap));
        estimate.setDataDate(LocalDate.of(2026, 10, 29));

        MailContent content = builder.build(TRADE_DATE, estimate);
        assertThat(content.getItems()).hasSize(1);
        assertThat(content.getUnavailableItems()).hasSize(1);
        assertThat(content.getQuoteDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(content.getDataCutoffDate()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    /**
     * 造一条持仓
     *
     * @param varietyKey 品种主键
     * @return 持仓
     */
    private HoldingVO holding(String varietyKey) {
        HoldingVO holding = new HoldingVO();
        holding.setVarietyKey(varietyKey);
        holding.setStatus(HoldingStatus.HOLDING);
        holding.setShares(new BigDecimal("1000"));
        return holding;
    }

    /**
     * 造一只红利品种
     *
     * @return 品种
     */
    private VarietyVO dividend() {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("OF:000922");
        variety.setVarietyCode("000922");
        variety.setVarietyName("中证红利ETF联接A");
        variety.setVarietyType(VarietyType.OF);
        variety.setCategory(FundCategory.DIVIDEND);
        variety.setTrackIndexCode("000922");
        return variety;
    }

    /**
     * 造一只混合型品种
     *
     * @return 品种
     */
    private VarietyVO mixed() {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("OF:005827");
        variety.setVarietyCode("005827");
        variety.setVarietyName("易方达蓝筹精选");
        variety.setVarietyType(VarietyType.OF);
        variety.setCategory(FundCategory.MIXED);
        return variety;
    }

    /**
     * 造一只债券型品种
     *
     * @return 品种
     */
    private VarietyVO bond() {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("OF:006327");
        variety.setVarietyCode("006327");
        variety.setVarietyName("易方达中债7-10年期国开行A");
        variety.setVarietyType(VarietyType.OF);
        variety.setCategory(FundCategory.BOND);
        return variety;
    }

    /**
     * 造一份债券监控总览
     *
     * @param alerting 是否触发换仓提醒
     * @return 总览
     */
    private BondAdvisoryVO bondAdvisory(boolean alerting) {
        BondAdvisoryVO advisory = new BondAdvisoryVO();
        advisory.setParamUsable(Boolean.TRUE);
        advisory.setSidewaysDaysThreshold(20);
        advisory.setWeakSlopeThreshold(new BigDecimal("0.50"));
        if (alerting) {
            advisory.setAlertCount(1);
            advisory.setSummary(List.of("006327 易方达中债：横盘，考虑换仓。横盘 25 个交易日，区间累计涨幅 +0.02%"));
        } else {
            advisory.setAlertCount(0);
            advisory.setSummary(List.of("006327 易方达中债：正常，横盘 3 个交易日，年化斜率 +1.20%，未达换仓阈值"));
        }
        return advisory;
    }

    /**
     * 造一份只有一只可预估基金的预估总览
     *
     * @return 预估总览
     */
    private EstimateBatchVO estimateWithOne() {
        EstimateBatchVO estimate = new EstimateBatchVO();
        estimate.setRequested(1);
        estimate.setEstimableCount(1);
        estimate.setUnavailableCount(0);
        EstimateVO vo = new EstimateVO();
        vo.setVarietyKey("OF:000922");
        vo.setVarietyCode("000922");
        vo.setVarietyName("中证红利ETF联接A");
        vo.setEstimable(true);
        vo.setEstimatedChange(new BigDecimal("0.52"));
        vo.setTier(EstimateTier.A);
        vo.setTierLabel("点估计");
        vo.setConfidence(EstimateConfidence.HIGH);
        vo.setConfidenceLabel("高");
        estimate.setItems(List.of(vo));
        return estimate;
    }

    /**
     * 造一份参数未配置的红利汇总
     *
     * @return 红利汇总
     */
    private DividendAdvisoryVO emptyAdvisory() {
        DividendAdvisoryVO advisory = new DividendAdvisoryVO();
        advisory.setParamUsable(false);
        advisory.setParamIssues(List.of("基准仓位未配置"));
        advisory.setItems(List.of());
        advisory.setSkippedItems(List.of());
        return advisory;
    }
}
