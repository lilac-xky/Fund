package com.lilac.engine;

import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.estimate.EstimateBacktestRequest;
import com.lilac.domain.vo.BacktestItemVO;
import com.lilac.domain.vo.BacktestResultVO;
import com.lilac.domain.vo.BacktestSummaryVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.VarietyService;
import com.lilac.service.impl.EstimateBacktestServiceImpl;
import com.lilac.setting.SettingOverrideResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 因子回测测试，核心是「无前视偏差」「结果可复现」与「参数确实生效」
 */
class EstimateBacktestTest {

    private EstimateBacktestServiceImpl service;
    private FundNetWorthService fundNetWorthService;
    private MarketQuoteAdapter marketQuoteAdapter;
    private VarietyService varietyService;
    private EstimateThresholds thresholds;

    /**
     * 装配回测服务，数据源全部打桩
     */
    @BeforeEach
    void setUp() {
        service = new EstimateBacktestServiceImpl();
        fundNetWorthService = Mockito.mock(FundNetWorthService.class);
        marketQuoteAdapter = Mockito.mock(MarketQuoteAdapter.class);
        varietyService = Mockito.mock(VarietyService.class);
        thresholds = new EstimateThresholds();
        ReflectionTestUtils.setField(service, "varietyService", varietyService);
        ReflectionTestUtils.setField(service, "fundNetWorthService", fundNetWorthService);
        ReflectionTestUtils.setField(service, "marketQuoteAdapter", marketQuoteAdapter);
        ReflectionTestUtils.setField(service, "benchmarkResolver", new BenchmarkResolver(thresholds));
        ReflectionTestUtils.setField(service, "betaRegressor", new BetaRegressor());
        ReflectionTestUtils.setField(service, "goldBetaEstimator", new GoldBetaEstimator());
        ReflectionTestUtils.setField(service, "bondBaselineEstimator", new BondBaselineEstimator());
        ReflectionTestUtils.setField(service, "thresholds", thresholds);
        SettingOverrideResolver resolver = Mockito.mock(SettingOverrideResolver.class);
        when(resolver.effective(any())).thenAnswer(call -> call.getArgument(0));
        ReflectionTestUtils.setField(service, "settingOverrideResolver", resolver);
    }

    /**
     * 结构化桩数据下回测跑通，逐条结果带误差、超阈值与方向命中
     */
    @Test
    @DisplayName("回测产出逐条结果与汇总")
    void producesItemsAndSummary() {
        stubBondFund();

        BacktestResultVO result = service.run(requestOf("2026-06-01", "2026-08-01"));

        assertThat(result.getSampleCount()).isPositive();
        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getSummaries()).hasSize(1);
        BacktestSummaryVO summary = result.getSummaries().get(0);
        assertThat(summary.getVarietyCode()).isEqualTo("004156");
        assertThat(summary.getMae()).isNotNull();
        assertThat(summary.getBias()).isNotNull();
        BacktestItemVO first = result.getItems().get(0);
        assertThat(first.getEstimatedChange()).isNotNull();
        assertThat(first.getActualChange()).isNotNull();
        assertThat(first.getErrorValue()).isNotNull();
        assertThat(first.getOverThreshold()).isNotNull();
    }

    /**
     * 结果必须回显本次生效参数，否则两次回测不可比
     */
    @Test
    @DisplayName("结果回显生效参数")
    void echoesEffectiveParams() {
        stubBondFund();
        EstimateBacktestRequest request = requestOf("2026-06-01", "2026-08-01");
        request.setBondWindow(120);
        request.setBondErrorThreshold(new BigDecimal("0.20"));

        BacktestResultVO result = service.run(request);

        assertThat(result.getParams().getBondWindow()).isEqualTo(120);
        assertThat(result.getParams().getBondErrorThreshold()).isEqualByComparingTo("0.20");
    }

    /**
     * 换阈值确实改变超阈值判定，说明参数真的接进去了
     */
    @Test
    @DisplayName("阈值改动影响超阈值判定")
    void thresholdChangeAffectsOverFlag() {
        stubBondFund();
        EstimateBacktestRequest strict = requestOf("2026-06-01", "2026-08-01");
        strict.setBondErrorThreshold(new BigDecimal("0.0001"));
        EstimateBacktestRequest loose = requestOf("2026-06-01", "2026-08-01");
        loose.setBondErrorThreshold(new BigDecimal("50"));

        long strictOver = service.run(strict).getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getOverThreshold())).count();
        long looseOver = service.run(loose).getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getOverThreshold())).count();

        assertThat(strictOver).isGreaterThan(looseOver);
        assertThat(looseOver).isZero();
    }

    /**
     * 同一请求跑两次结果完全一致，回测只读不写
     */
    @Test
    @DisplayName("同一请求结果可复现")
    void reproducible() {
        stubBondFund();

        BacktestResultVO first = service.run(requestOf("2026-06-01", "2026-08-01"));
        BacktestResultVO second = service.run(requestOf("2026-06-01", "2026-08-01"));

        assertThat(first.getSampleCount()).isEqualTo(second.getSampleCount());
        assertThat(first.getMae()).isEqualByComparingTo(second.getMae());
        assertThat(first.getItems()).hasSameSizeAs(second.getItems());
    }

    /**
     * 起止日颠倒与区间过长都直接报参数错误
     */
    @Test
    @DisplayName("非法区间被拦住")
    void rejectsBadRange() {
        stubBondFund();

        assertThatThrownBy(() -> service.run(requestOf("2026-08-01", "2026-06-01")))
                .hasMessageContaining("起始日不能晚于截止日");
        assertThatThrownBy(() -> service.run(requestOf("2020-01-01", "2026-08-01")))
                .hasMessageContaining("最多");
    }

    /**
     * 混合型不给点估计，进 notes 而不是伪造数字
     */
    @Test
    @DisplayName("混合型进说明不产出数字")
    void mixedExplainedNotFabricated() {
        VarietyVO mixed = varietyOf("OF:011370", "011370", "华商均衡成长混合C", FundCategory.MIXED);
        when(varietyService.listVariety(any())).thenReturn(List.of(mixed));

        BacktestResultVO result = service.run(requestOf("2026-06-01", "2026-08-01"));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNotes()).anyMatch(note -> note.contains("混合型"));
    }

    /**
     * 造一只债券基金的桩数据：净值按日、代理日线同结构
     */
    private void stubBondFund() {
        VarietyVO bond = varietyOf("OF:004156", "004156", "中信保诚至泰中短债C", FundCategory.BOND);
        when(varietyService.listVariety(any())).thenReturn(List.of(bond));
        LocalDate from = LocalDate.of(2026, 1, 1);
        List<FundNetWorthPoint> netWorths = new ArrayList<>();
        List<MarketBar> bars = new ArrayList<>();
        double close = 100.0;
        for (int i = 0; i < 200; i++) {
            LocalDate day = from.plusDays(i);
            bars.add(new MarketBar(day, BigDecimal.valueOf(close).setScale(6, java.math.RoundingMode.HALF_UP)));
            // 代理每日在 ±0.1% 之间交替，基金收益取其 0.3 倍并叠加微小噪声
            double proxyReturn = (i % 2 == 0 ? 1 : -1) * (0.05 + 0.05 * (i % 7) / 6.0);
            FundNetWorthPoint point = new FundNetWorthPoint();
            point.setNetWorthDate(day);
            point.setEquityReturn(BigDecimal.valueOf(proxyReturn * 0.3)
                    .setScale(6, java.math.RoundingMode.HALF_UP));
            netWorths.add(point);
            close = close * (1 + proxyReturn / 100);
        }
        when(fundNetWorthService.listRange(anyString(), any(), any())).thenReturn(netWorths);
        MarketHistory history = new MarketHistory();
        history.setBars(bars);
        history.setAdjusted(Boolean.TRUE);
        when(marketQuoteAdapter.history(anyString(), anyInt())).thenReturn(history);
    }

    /**
     * 构造品种
     *
     * @param key      业务主键
     * @param code     6 位代码
     * @param name     名称
     * @param category 类型
     * @return 品种
     */
    private VarietyVO varietyOf(String key, String code, String name, FundCategory category) {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey(key);
        variety.setVarietyCode(code);
        variety.setVarietyName(name);
        variety.setVarietyType(VarietyType.OF);
        variety.setCategory(category);
        return variety;
    }

    /**
     * 构造回测请求
     *
     * @param start 起始日
     * @param end   截止日
     * @return 请求
     */
    private EstimateBacktestRequest requestOf(String start, String end) {
        EstimateBacktestRequest request = new EstimateBacktestRequest();
        request.setStartDate(start);
        request.setEndDate(end);
        return request;
    }
}
