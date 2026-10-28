package com.lilac.engine;

import com.lilac.domain.dto.estimate.CalibrationRequest;
import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.EstimateSnapshot;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.FundNetWorth;
import com.lilac.domain.vo.CalibrationItemVO;
import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.domain.vo.EstimateErrorTrendVO;
import com.lilac.mapper.EstimateSnapshotMapper;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.mapper.FundNetWorthMapper;
import com.lilac.service.EstimateCalibrationService;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.EstimateService;
import com.lilac.service.VarietyService;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import com.lilac.service.FundSnapshotService;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 晚间校准验收测试，净值与行情用桩隔离，数据库与 Redis 走真实实例
 */
@SpringBootTest
@Transactional
class EstimateCalibrationIntegrationTest {

    private static final LocalDate DAY1 = LocalDate.now().minusDays(12);
    private static final LocalDate DAY2 = LocalDate.now().minusDays(11);
    private static final LocalDate DAY3 = LocalDate.now().minusDays(10);
    private static final LocalDate DAY4 = LocalDate.now().minusDays(9);

    @Autowired
    private EstimateCalibrationService calibrationService;

    @Autowired
    private EstimateErrorService estimateErrorService;

    @Autowired
    private EstimateService estimateService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private EstimateSnapshotMapper estimateSnapshotMapper;

    @Autowired
    private FundNetWorthMapper fundNetWorthMapper;

    @Autowired
    private FundCatalogMapper fundCatalogMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private com.lilac.service.EstimateGoldBetaService estimateGoldBetaService;

    @MockitoBean
    private MarketQuoteAdapter marketQuoteAdapter;

    @MockitoBean
    private FundSnapshotService fundSnapshotService;

    /**
     * 清掉上一次用例留下的留存与缓存，避免跨用例串数据
     */
    @BeforeEach
    void setUp() {
        estimateSnapshotMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        redissonClient.getKeys().deleteByPattern("fund:estimate:*");
    }

    /**
     * 误差历史连续写入 3 日后可按基金查出误差序列，且每天只留一行
     */
    @Test
    @DisplayName("误差历史连续三日可查趋势且不重复累计")
    void errorHistoryAccumulates() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        saveNetWorth("990001", DAY1, "-0.10", "-1.00");
        saveNetWorth("990001", DAY2, "-0.08", "-0.80");
        saveNetWorth("990001", DAY3, "0.50", "0.60");
        for (LocalDate day : List.of(DAY1, DAY2, DAY3)) {
            saveSnapshot("OF:990001", day, "STOCK", "INDEX_ONLY", "A", "0.50", null, null, null);
        }

        for (LocalDate day : List.of(DAY1, DAY2, DAY3)) {
            CalibrationResultVO result = calibrationService.calibrate(requestOf(day));
            assertThat(result.getCalibratedCount()).isEqualTo(1);
            assertThat(result.getWrittenCount()).isEqualTo(1);
        }
        // 同一天重跑只覆盖不新增
        calibrationService.calibrate(requestOf(DAY3));

        EstimateErrorTrendVO trend = calibrationService.trend("OF:990001", 60);

        assertThat(trend.getSampleCount()).isEqualTo(3);
        assertThat(trend.getSeries()).hasSize(3);
        assertThat(trend.getLatestTradeDate()).isEqualTo(DAY3);
        assertThat(trend.getCategory()).isEqualTo(FundCategory.STOCK);
    }

    /**
     * 股票型误差超 0.3 被标记，未超的不标记
     */
    @Test
    @DisplayName("超 0.3 的股票型被标记，未超的不标记")
    void stockThresholdMarked() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        saveVariety("OF:990002", VarietyType.OF, "990002", "易方达沪深300联接A", FundCategory.STOCK);
        saveNetWorth("990001", DAY1, "-0.50", "-0.80");
        saveNetWorth("990002", DAY1, "0.50", "0.60");
        saveSnapshot("OF:990001", DAY1, "STOCK", "INDEX_ONLY", "A", "0.50", null, null, null);
        saveSnapshot("OF:990002", DAY1, "STOCK", "INDEX_ONLY", "A", "0.50", null, null, null);

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        assertThat(result.getCalibratedCount()).isEqualTo(2);
        assertThat(result.getOverThresholdCount()).isEqualTo(1);
        CalibrationItemVO over = result.getOverThresholdItems().get(0);
        assertThat(over.getVarietyCode()).isEqualTo("990001");
        assertThat(over.getErrorValue()).isEqualByComparingTo("1.300000");
        CalibrationItemVO under = result.getItems().stream()
                .filter(item -> "990002".equals(item.getVarietyCode())).findFirst().orElseThrow();
        assertThat(under.getOverThreshold()).isFalse();
    }

    /**
     * 债券型用 0.10 的独立阈值，同样的误差不会套用 0.3
     */
    @Test
    @DisplayName("债券型使用独立阈值 0.10")
    void bondUsesOwnThreshold() {
        saveVariety("OF:990003", VarietyType.OF, "990003", "招商产业债券A", FundCategory.BOND);
        saveNetWorth("990003", DAY1, "0.20", "0.02");
        saveSnapshot("OF:990003", DAY1, "BOND", "BOND_PROXY", "A", "0.20", null, null, null);

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        CalibrationItemVO item = result.getItems().get(0);
        assertThat(item.getThresholdValue()).isEqualByComparingTo("0.10");
        assertThat(item.getOverThreshold()).isTrue();
        assertThat(item.getRemarks()).anyMatch(note -> note.contains("独立阈值"));
    }

    /**
     * 混合型不判点估计误差，只记录方向命中、区间覆盖与区间宽度
     */
    @Test
    @DisplayName("混合型记录方向命中与区间覆盖")
    void mixedRecordsDirectionAndInterval() {
        saveVariety("OF:990004", VarietyType.OF, "990004", "易方达蓝筹精选混合", FundCategory.MIXED);
        saveNetWorth("990004", DAY1, "1.20", "1.20");
        saveSnapshotWithDirection("OF:990004", DAY1, "MIXED", "STOCK_WEIGHTED", "B", "UP",
                "-0.90", "1.80");

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        CalibrationItemVO item = result.getItems().get(0);
        assertThat(item.getThresholdValue()).isNull();
        assertThat(item.getEstimatedChange()).isNull();
        assertThat(item.getDirectionHit()).isTrue();
        assertThat(item.getInInterval()).isTrue();
        assertThat(item.getIntervalWidth()).isEqualByComparingTo("2.700000");
        assertThat(item.getOverThreshold()).isFalse();
        assertThat(item.getRemarks()).anyMatch(note -> note.contains("不参与点估计阈值判定"));
    }

    /**
     * 混合型实际值跑出区间时判超阈值，哪怕方向命中
     */
    @Test
    @DisplayName("混合型落在区间外判超阈值")
    void mixedOutsideIntervalIsOver() {
        saveVariety("OF:990004", VarietyType.OF, "990004", "易方达蓝筹精选混合", FundCategory.MIXED);
        saveNetWorth("990004", DAY1, "-3.20", "-3.20");
        saveSnapshotWithDirection("OF:990004", DAY1, "MIXED", "STOCK_WEIGHTED", "B", "DOWN",
                "-0.90", "0.30");

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        CalibrationItemVO item = result.getItems().get(0);
        assertThat(item.getDirectionHit()).isTrue();
        assertThat(item.getInInterval()).isFalse();
        assertThat(item.getOverThreshold()).isTrue();
    }

    /**
     * 混合型连续多期方向不达标时降级为 C 档，并按说明提示无法预估
     */
    @Test
    @DisplayName("混合型方向长期不达标降级为 C 档")
    void mixedDegradesToTierC() {
        saveVariety("OF:990005", VarietyType.OF, "990005", "诺安成长混合", FundCategory.MIXED);
        // 先铺 40 期方向全错的历史，判断滚动窗口据此降级
        for (int i = 0; i < 40; i++) {
            LocalDate day = DAY1.minusDays(60 - i);
            saveErrorRow("OF:990005", day, null, "-1.00", true, false, false, "2.00", "B");
        }
        saveNetWorth("990005", DAY1, "-1.00", "-1.00");
        saveSnapshotWithDirection("OF:990005", DAY1, "MIXED", "STOCK_WEIGHTED", "B", "UP",
                "-2.50", "0.50");

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        CalibrationItemVO item = result.getItems().get(0);
        assertThat(item.getRollingTier()).isEqualTo(EstimateTier.C);
        assertThat(item.getRemarks()).anyMatch(note -> note.contains("降级为无法预估"));
    }

    /**
     * 黄金基金记录 β 与残差 P90，落进独立记录表供后续核对
     */
    @Test
    @DisplayName("黄金基金记录 β 与残差 P90")
    void goldBetaRecorded() {
        saveVariety("OF:990006", VarietyType.OF, "990006", "华安黄金ETF联接A", FundCategory.GOLD);
        saveNetWorth("990006", DAY1, "0.20", "0.30");
        saveSnapshotWithBeta("OF:990006", DAY1, "0.913411", "-0.607661", "0.259087");

        calibrationService.calibrate(requestOf(DAY1));

        List<com.lilac.domain.vo.GoldBetaRecordVO> rows = estimateGoldBetaService.list("OF:990006", null, null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBeta()).isBetween(new BigDecimal("0.90"), new BigDecimal("0.92"));
        assertThat(rows.get(0).getResidualP90()).isEqualByComparingTo("0.259087");
        assertThat(rows.get(0).getRho()).isEqualByComparingTo("-0.607661");
    }

    /**
     * 单日出现大误差时档位保持不动，不因一天翻转
     */
    @Test
    @DisplayName("单日大误差不翻转档位")
    void tierStableAgainstSingleOutlier() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        // 铺 40 期误差很小的历史，档位应为 A
        for (int i = 0; i < 40; i++) {
            LocalDate day = DAY1.minusDays(60 - i);
            saveErrorRow("OF:990001", day, "0.50", "0.55", false, null, null, null, "A");
        }
        saveNetWorth("990001", DAY1, "5.00", "0.60");
        saveSnapshot("OF:990001", DAY1, "STOCK", "INDEX_ONLY", "A", "5.00", null, null, null);

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY1));

        CalibrationItemVO item = result.getItems().get(0);
        assertThat(item.getOverThreshold()).isTrue();
        assertThat(item.getRollingTier()).isEqualTo(EstimateTier.A);
    }

    /**
     * 连续三期超阈值时给出建议停用提示
     */
    @Test
    @DisplayName("连续三期超阈值给出停用建议")
    void suspendHintAfterThreePeriods() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        for (LocalDate day : List.of(DAY1, DAY2, DAY3)) {
            saveErrorRow("OF:990001", day, "3.00", "-0.50", true, null, null, null, "A");
            saveNetWorth("990001", day, "3.00", "-0.50");
            saveSnapshot("OF:990001", day, "STOCK", "INDEX_ONLY", "A", "3.00", null, null, null);
        }

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY3));
        calibrationService.calibrate(requestOf(DAY3));

        assertThat(result.getItems()).hasSize(1);
        EstimateErrorTrendVO trend = calibrationService.trend("OF:990001", 30);
        assertThat(trend.getConsecutiveOverCount()).isGreaterThanOrEqualTo(3);
        assertThat(trend.getSuggestSuspend()).isTrue();
        assertThat(calibrationService.suspensionHints(DAY3))
                .anyMatch(hint -> "OF:990001".equals(hint.getVarietyKey()));
    }

    /**
     * 当日净值未公布时进缺口清单并说明原因，绝不写成误差为 0 的假记录
     */
    @Test
    @DisplayName("净值未公布时进缺口清单不写假记录")
    void missingNetWorthGoesToGap() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        saveNetWorth("990001", DAY4, "0.30", "0.40");
        saveSnapshot("OF:990001", DAY3, "STOCK", "INDEX_ONLY", "A", "0.50", null, null, null);

        CalibrationResultVO result = calibrationService.calibrate(requestOf(DAY3));

        assertThat(result.getCalibratedCount()).isZero();
        assertThat(result.getGapCount()).isEqualTo(1);
        assertThat(result.getGaps().get(0).getReason()).contains("净值未公布");
        assertThat(estimateErrorService.listEstimateError(new EstimateErrorQueryRequest())).isEmpty();
    }

    /**
     * 当日预估留存缺失时不编造校准结果，只报缺口
     */
    @Test
    @DisplayName("预估留存缺失时报缺口")
    void missingSnapshotGoesToGap() {
        saveVariety("OF:990001", VarietyType.OF, "990001", "招商中证白酒指数A", FundCategory.STOCK);
        saveNetWorth("990001", DAY1, "0.30", "0.40");

        CalibrationRequest request = requestOf(DAY1);
        request.setVarietyKeys(List.of("OF:990001"));
        CalibrationResultVO result = calibrationService.calibrate(request);

        assertThat(result.getCalibratedCount()).isZero();
        assertThat(result.getGaps().get(0).getReason()).contains("预估留存缺失");
    }

    /**
     * 白天跑一次预估后当日留存即可被晚间校准读到，形成闭环
     */
    @Test
    @DisplayName("预估后留存可直接供校准读取")
    void estimateRetainsSnapshot() {
        saveVariety("ETF:510300", VarietyType.ETF, "510300", "沪深300ETF", FundCategory.ETF);
        saveCatalog("510300", "沪深300ETF", "指数型-股票");
        when(marketQuoteAdapter.quotes(anyList()))
                .thenReturn(Map.of("sh510300", okQuote("sh510300", "0.85")));

        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setVarietyKeys(List.of("ETF:510300"));
        request.setRefresh(Boolean.TRUE);
        EstimateBatchVO batch = estimateService.estimateBatch(request);

        assertThat(batch.getEstimableCount()).isEqualTo(1);
        List<EstimateSnapshot> rows = estimateSnapshotMapper.selectListByQuery(QueryWrapper.create()
                .eq(EstimateSnapshot::getVarietyKey, "ETF:510300"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEstimatedChange()).isEqualByComparingTo("0.8500");
        assertThat(rows.get(0).getOutputTier()).isEqualTo("A");
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
     * 写入一条净值，供校准取当日实际涨跌
     *
     * @param code     6 位代码
     * @param date     净值日期
     * @param netWorth 单位净值
     * @param equity   当日回报，百分点
     */
    private void saveNetWorth(String code, LocalDate date, String netWorth, String equity) {
        FundNetWorth row = new FundNetWorth();
        row.setFundCode(code);
        row.setNetWorthDate(date);
        row.setUnitNetWorth(new BigDecimal(netWorth));
        row.setEquityReturn(new BigDecimal(equity));
        fundNetWorthMapper.insertSelective(row);
    }

    /**
     * 写入一条当日预估留存
     *
     * @param varietyKey 业务主键
     * @param date       交易日
     * @param category   基金类型
     * @param branch     分支
     * @param tier       档位
     * @param estimated  点估计
     * @param low        区间下限
     * @param high       区间上限
     * @param direction  方向
     */
    private void saveSnapshot(String varietyKey, LocalDate date, String category, String branch, String tier,
                              String estimated, String low, String high, String direction) {
        EstimateSnapshot row = new EstimateSnapshot();
        row.setVarietyKey(varietyKey);
        row.setTradeDate(date);
        row.setCategory(category);
        row.setBranch(branch);
        row.setOutputTier(tier);
        row.setEstimatedChange(estimated == null ? null : new BigDecimal(estimated));
        row.setIntervalLow(low == null ? null : new BigDecimal(low));
        row.setIntervalHigh(high == null ? null : new BigDecimal(high));
        row.setDirection(direction);
        row.setCoverage(new BigDecimal("100"));
        row.setEvaluable(Boolean.TRUE);
        estimateSnapshotMapper.insertSelective(row);
    }

    /**
     * 写入一条带方向的混合型留存
     *
     * @param varietyKey 业务主键
     * @param date       交易日
     * @param category   基金类型
     * @param branch     分支
     * @param tier       档位
     * @param direction  方向
     * @param low        区间下限
     * @param high       区间上限
     */
    private void saveSnapshotWithDirection(String varietyKey, LocalDate date, String category, String branch,
                                           String tier, String direction, String low, String high) {
        saveSnapshot(varietyKey, date, category, branch, tier, null, low, high, direction);
    }

    /**
     * 写入一条带黄金 β 的留存
     *
     * @param varietyKey 业务主键
     * @param date       交易日
     * @param beta       β
     * @param rho        ρ
     * @param p90        残差 P90
     */
    private void saveSnapshotWithBeta(String varietyKey, LocalDate date, String beta, String rho, String p90) {
        saveSnapshot(varietyKey, date, "GOLD", "GOLD_PROXY", "A", "0.30", null, null, null);
        EstimateSnapshot row = estimateSnapshotMapper.selectOneByQuery(QueryWrapper.create()
                .eq(EstimateSnapshot::getVarietyKey, varietyKey)
                .eq(EstimateSnapshot::getTradeDate, date));
        row.setBeta(new BigDecimal(beta));
        row.setRho(new BigDecimal(rho));
        row.setResidualP90(new BigDecimal(p90));
        estimateSnapshotMapper.update(row);
    }

    /**
     * 直接写入一条历史误差记录
     *
     * @param varietyKey 业务主键
     * @param date       交易日
     * @param estimated  预估涨跌幅
     * @param actual     实际涨跌幅
     * @param over       是否超阈值
     * @param directionHit 方向是否命中
     * @param inInterval 是否落在区间内
     * @param width      区间宽度
     * @param tier       档位
     */
    private void saveErrorRow(String varietyKey, LocalDate date, String estimated, String actual, boolean over,
                              Boolean directionHit, Boolean inInterval, String width, String tier) {
        com.lilac.domain.dto.estimate.EstimateErrorSaveRequest request =
                new com.lilac.domain.dto.estimate.EstimateErrorSaveRequest();
        request.setVarietyKey(varietyKey);
        request.setTradeDate(date);
        request.setEstimatedChange(estimated == null ? null : new BigDecimal(estimated));
        request.setActualChange(new BigDecimal(actual));
        request.setErrorValue(estimated == null ? null : new BigDecimal(estimated).subtract(new BigDecimal(actual)));
        request.setOverThreshold(over);
        request.setDirectionHit(directionHit);
        request.setInInterval(inInterval);
        request.setIntervalWidth(width == null ? null : new BigDecimal(width));
        request.setOutputTier(tier);
        request.setEstimateSource("SNAPSHOT");
        estimateErrorService.saveEstimateError(request);
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
     * 构造校准请求
     *
     * @param day 交易日
     * @return 请求
     */
    private CalibrationRequest requestOf(LocalDate day) {
        CalibrationRequest request = new CalibrationRequest();
        request.setTradeDate(day);
        return request;
    }

    /**
     * 构造可用行情
     *
     * @param symbol  带前缀代码
     * @param percent 涨跌幅
     * @return 行情
     */
    private MarketQuote okQuote(String symbol, String percent) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(MarketSymbol.of(symbol));
        quote.setChangePercent(new BigDecimal(percent));
        quote.setPrice(new BigDecimal("10.00"));
        quote.setPreviousClose(new BigDecimal("9.90"));
        quote.setTradeDate(DAY4);
        quote.setStatus(QuoteStatus.OK);
        quote.setSource("stub");
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.TRUE);
        return quote;
    }
}
