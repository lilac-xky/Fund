package com.lilac.engine;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateVO;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.mapper.HoldingMapper;
import com.lilac.service.EstimateService;
import com.lilac.service.FundSnapshotService;
import com.lilac.service.VarietyService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 预估验收测试，行情与净值用桩隔离，数据库与 Redis 走真实实例
 */
@SpringBootTest
@Transactional
class EstimateIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Autowired
    private EstimateService estimateService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private FundCatalogMapper fundCatalogMapper;

    @Autowired
    private HoldingMapper holdingMapper;

    @Autowired
    private RedissonClient redissonClient;

    @MockitoBean
    private MarketQuoteAdapter marketQuoteAdapter;

    @MockitoBean
    private FundSnapshotService fundSnapshotService;

    /**
     * 清掉上一次用例留下的缓存与全集行，避免跨用例串数据
     */
    @BeforeEach
    void setUp() {
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        // 未指定品种时预预估按全部持有中持仓取数，dev 库的真实持仓必须隔离
        holdingMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        redissonClient.getKeys().deleteByPattern("fund:estimate:*");
    }

    /**
     * 行情整体取不到时，每只基金都进不可预估组，且任何一个数字字段都为 null
     */
    @Test
    @DisplayName("行情取不到时不输出任何推算数字")
    void noQuoteYieldsNoNumbers() {
        saveVariety("ETF:510300", VarietyType.ETF, "510300", "沪深300ETF", FundCategory.ETF);
        saveCatalog("510300", "沪深300ETF", "指数型-股票");
        registerEtfQuote("sh510300", null);

        EstimateBatchVO result = estimateService.estimateBatch(requestOf(List.of("ETF:510300")));

        assertThat(result.getUnavailableCount()).isEqualTo(1);
        assertThat(result.getEstimableCount()).isZero();
        assertThat(result.getOverallChange()).isNull();
        EstimateVO vo = result.getUnavailableItems().get(0);
        assertThat(vo.getEstimatedChange()).isNull();
        assertThat(vo.getDirection()).isNull();
        assertThat(vo.getIntervalLow()).isNull();
        assertThat(vo.getIntervalHigh()).isNull();
        assertThat(vo.getEstimable()).isFalse();
        assertThat(vo.getUnavailableReason()).contains("无法预估");
    }

    /**
     * 指数型基金走指数直取，结果与对标指数涨跌一致
     */
    @Test
    @DisplayName("指数型基金结果与指数涨跌一致")
    void indexFundMatchesBenchmark() {
        saveVariety("OF:110020", VarietyType.OF, "110020", "易方达沪深300ETF联接A", FundCategory.STOCK);
        saveCatalog("110020", "易方达沪深300ETF联接A", "指数型-股票");
        when(marketQuoteAdapter.quotes(anyList())).thenReturn(Map.of("sh000300", okQuote("sh000300", "0.85")));

        EstimateBatchVO result = estimateService.estimateBatch(requestOf(List.of("OF:110020")));

        assertThat(result.getEstimableCount()).isEqualTo(1);
        EstimateVO vo = result.getItems().get(0);
        assertThat(vo.getEstimatedChange()).isEqualByComparingTo("0.8500");
        assertThat(vo.getTierLabel()).isEqualTo("点估计");
        assertThat(vo.getProxySymbol()).isEqualTo("sh000300");
    }

    /**
     * 列表为空时按全部持有中的持仓预估，未录持仓明确报错
     */
    @Test
    @DisplayName("未录持仓时给出明确提示")
    void noHoldingGivesHint() {
        EstimateBatchRequest request = new EstimateBatchRequest();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> estimateService.estimateBatch(request))
                .hasMessageContaining("尚未录入持仓");
    }

    /**
     * 超过 30 只上限时按批处理，任何一只都不静默丢弃
     */
    @Test
    @DisplayName("超过 30 只上限时提示分批且不丢数据")
    void overLimitChunked() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            String code = String.format("15%04d", 1000 + i);
            saveVariety("ETF:" + code, VarietyType.ETF, code, "测试ETF" + i, FundCategory.ETF);
            keys.add("ETF:" + code);
        }
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        for (String key : keys) {
            String code = key.substring(key.indexOf(':') + 1);
            quotes.put("sz" + code, okQuote("sz" + code, "0.50"));
        }
        when(marketQuoteAdapter.quotes(anyList())).thenReturn(quotes);

        EstimateBatchVO result = estimateService.estimateBatch(requestOf(keys));

        assertThat(result.getRequested()).isEqualTo(32);
        assertThat(result.getEstimableCount()).isEqualTo(32);
        assertThat(result.getNotice()).contains("30").contains("未丢弃");
    }

    /**
     * 停牌重仓股占多数时整只进不可预估，原因可读且不参与整体统计
     */
    @Test
    @DisplayName("停牌重仓股导致不可预估且原因可读")
    void suspendedHoldingsUnavailable() {
        saveVariety("OF:161725", VarietyType.OF, "161725", "招商中证白酒指数A", FundCategory.STOCK);
        saveCatalog("161725", "招商中证白酒指数A", "股票型");
        when(fundSnapshotService.load(anyString(), any())).thenReturn(snapshotOf("161725",
                new HoldingItemVO("600519", "贵州茅台", new BigDecimal("47.14")),
                new HoldingItemVO("000858", "五粮液", new BigDecimal("47.13"))));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        MarketQuote suspended = okQuote("sh600519", null);
        suspended.setStatus(QuoteStatus.SUSPENDED);
        suspended.setUnavailableReason("当日无成交（停牌或未交易）");
        quotes.put("sh600519", suspended);
        quotes.put("sz000858", MarketQuote.unavailable(MarketSymbol.of("sz000858"), QuoteStatus.NO_DATA, "无数据"));
        when(marketQuoteAdapter.quotes(anyList())).thenReturn(quotes);

        EstimateBatchVO result = estimateService.estimateBatch(requestOf(List.of("OF:161725")));

        assertThat(result.getUnavailableCount()).isEqualTo(1);
        assertThat(result.getOverallChange()).isNull();
        EstimateVO vo = result.getUnavailableItems().get(0);
        assertThat(vo.getUnavailableReason()).contains("停牌");
        assertThat(vo.getUnavailableDetail()).contains("600519");
    }

    /**
     * 单只品种走批量接口时能带出类型与档位，与批量结论一致
     */
    @Test
    @DisplayName("单只品种预估带出类型与档位")
    void singleEstimateHasTier() {
        saveVariety("ETF:510300", VarietyType.ETF, "510300", "沪深300ETF", FundCategory.ETF);
        saveCatalog("510300", "沪深300ETF", "指数型-股票");
        when(marketQuoteAdapter.quotes(anyList())).thenReturn(Map.of("sh510300", okQuote("sh510300", "-0.42")));

        EstimateVO vo = estimateService.estimateBatch(requestOf(List.of("ETF:510300"))).getItems().get(0);

        assertThat(vo.getVarietyKey()).isEqualTo("ETF:510300");
        assertThat(vo.getCategory()).isEqualTo(FundCategory.ETF);
        assertThat(vo.getCategoryLabel()).isEqualTo("场内 ETF");
        assertThat(vo.getTier()).isNotNull();
        assertThat(vo.getTierLabel()).isNotBlank();
        assertThat(vo.getConfidenceLabel()).isNotBlank();
        assertThat(vo.getEstimatedChange()).isEqualByComparingTo("-0.4200");
        assertThat(vo.getRemarks()).anyMatch(note -> note.contains("预估基于重仓股与指数推算"));
    }

    /**
     * 写入品种
     *
     * @param varietyKey 业务主键
     * @param type       品种类型
     * @param code       6 位代码
     * @param name       名称
     * @param category   基金类型
     */
    private void saveVariety(String varietyKey, VarietyType type, String code, String name, FundCategory category) {
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey(varietyKey);
        request.setVarietyType(type);
        request.setVarietyCode(code);
        request.setVarietyName(name);
        request.setCategory(category);
        request.setConfirmed(Boolean.TRUE);
        varietyService.saveVariety(request);
    }

    /**
     * 写入全集行，提供原始类型供分支判断
     *
     * @param code     6 位代码
     * @param name     名称
     * @param fundType 原始类型
     */
    private void saveCatalog(String code, String name, String fundType) {
        FundCatalog catalog = new FundCatalog();
        catalog.setFundCode(code);
        catalog.setFundName(name);
        catalog.setFundType(fundType);
        fundCatalogMapper.insertSelective(catalog);
    }

    /**
     * 注册场内 ETF 的行情桩，行情为 null 表示取不到
     *
     * @param symbol 带前缀代码
     * @param quote  行情
     */
    private void registerEtfQuote(String symbol, MarketQuote quote) {
        if (quote == null) {
            when(marketQuoteAdapter.quotes(anyList())).thenReturn(Map.of());
            when(marketQuoteAdapter.history(anyString(), anyInt())).thenReturn(new MarketHistory());
            return;
        }
        when(marketQuoteAdapter.quotes(anyList())).thenReturn(Map.of(symbol, quote));
    }

    /**
     * 构造请求
     *
     * @param keys 品种业务主键
     * @return 请求
     */
    private EstimateBatchRequest requestOf(List<String> keys) {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setVarietyKeys(keys);
        request.setRefresh(Boolean.TRUE);
        return request;
    }

    /**
     * 构造可用行情
     *
     * @param symbol  带前缀代码
     * @param percent 涨跌幅，null 表示无涨跌
     * @return 行情
     */
    private MarketQuote okQuote(String symbol, String percent) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(MarketSymbol.of(symbol));
        quote.setChangePercent(percent == null ? null : new BigDecimal(percent));
        quote.setPrice(new BigDecimal("10.00"));
        quote.setPreviousClose(new BigDecimal("9.90"));
        quote.setTradeDate(TODAY);
        quote.setStatus(QuoteStatus.OK);
        quote.setSource("stub");
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.TRUE);
        return quote;
    }

    /**
     * 构造净值与持仓
     *
     * @param code     6 位代码
     * @param holdings 持仓明细
     * @return 快照
     */
    private FundSnapshotVO snapshotOf(String code, HoldingItemVO... holdings) {
        FundSnapshotVO snapshot = new FundSnapshotVO();
        snapshot.setCode(code);
        snapshot.setName("测试基金");
        snapshot.setNetWorthDate(LocalDate.of(2026, 10, 27));
        snapshot.setHoldingPeriod("2026Q2");
        snapshot.setHoldingCutoffDate(LocalDate.of(2026, 6, 30));
        snapshot.setHoldingDisclosureDate(LocalDate.of(2026, 7, 21));
        snapshot.setStockHoldings(List.of(holdings));
        snapshot.setDegraded(Boolean.FALSE);
        return snapshot;
    }

    /**
     * 构造汇率桩，供港股折算
     *
     * @return 汇率
     */
    private ForexRate forexOf() {
        ForexRate rate = new ForexRate();
        rate.setPair("HKDCNY");
        rate.setRate(new BigDecimal("0.8500000000"));
        rate.setPreviousRate(new BigDecimal("0.8480000000"));
        rate.setChangeRate(new BigDecimal("0.00236000"));
        rate.setTradeDate(TODAY);
        rate.setSource("sina");
        return rate;
    }
}
