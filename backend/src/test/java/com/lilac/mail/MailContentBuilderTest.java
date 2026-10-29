package com.lilac.mail;

import com.lilac.domain.dto.holding.HoldingQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.DividendAdvisoryVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.domain.vo.HoldingVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.EstimateConfidence;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HoldingStatus;
import com.lilac.enums.VarietyType;
import com.lilac.service.DividendAdviceService;
import com.lilac.service.GridAdviceService;
import com.lilac.service.HoldingService;
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
    private MailContentBuilder builder;

    /**
     * 五类业务全部用桩，装配逻辑单独验证
     */
    @BeforeEach
    void setUp() {
        holdingService = mock(HoldingService.class);
        varietyService = mock(VarietyService.class);
        dividendAdviceService = mock(DividendAdviceService.class);
        indexDividendService = mock(IndexDividendService.class);
        gridAdviceService = mock(GridAdviceService.class);
        builder = new MailContentBuilder(holdingService, varietyService, dividendAdviceService,
                indexDividendService, gridAdviceService);
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
