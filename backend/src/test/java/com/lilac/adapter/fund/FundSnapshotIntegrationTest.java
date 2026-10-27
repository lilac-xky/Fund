package com.lilac.adapter.fund;

import com.lilac.adapter.model.FundAssetAllocationPoint;
import com.lilac.adapter.model.FundHoldingItem;
import com.lilac.adapter.model.FundHoldingSnapshot;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.FundPingzhongData;
import com.lilac.adapter.model.FundReportDisclosure;
import com.lilac.adapter.model.HoldingKind;
import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.vo.FundSnapshotVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.FundCatalogMapper;
import com.lilac.mapper.FundNetWorthMapper;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.FundSnapshotService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 净值与持仓验收测试，三个外部接口用桩隔离，数据库与 Redis 走真实实例
 */
@SpringBootTest
@Transactional
class FundSnapshotIntegrationTest {

    // 用不存在的代码做测试标的，避免与真实冒烟落库的净值混在一起
    private static final String CODE = "990010";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final String CACHE_KEY = "fund:fallback:eastmoney:" + CODE;

    @Autowired
    private FundSnapshotService fundSnapshotService;

    @Autowired
    private FundNetWorthService fundNetWorthService;

    @Autowired
    private FundCatalogMapper fundCatalogMapper;

    @Autowired
    private FundNetWorthMapper fundNetWorthMapper;

    @Autowired
    private RedissonClient redissonClient;

    @MockitoBean
    private FundPingzhongDataAdapter fundPingzhongDataAdapter;

    @MockitoBean
    private FundHoldingAdapter fundHoldingAdapter;

    @MockitoBean
    private FundDisclosureAdapter fundDisclosureAdapter;

    /**
     * 固定全集与披露日，清掉上一次用例与冒烟留下的净值与降级缓存
     */
    @BeforeEach
    void setUp() {
        fundCatalogMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        FundCatalog catalog = new FundCatalog();
        catalog.setFundCode(CODE);
        catalog.setFundName("大成中证红利指数A");
        catalog.setFundType("指数型-股票");
        fundCatalogMapper.insertSelective(catalog);
        when(fundDisclosureAdapter.fetchRegularReports(anyString())).thenReturn(List.of(
                new FundReportDisclosure("2026Q1", LocalDate.of(2026, 4, 22), Boolean.FALSE, "一季报"),
                new FundReportDisclosure("2026Q2", LocalDate.of(2026, 7, 21), Boolean.TRUE, "二季报"),
                new FundReportDisclosure("2026Q3", LocalDate.of(2026, 10, 27), Boolean.FALSE, "三季报")));
        redissonClient.getBucket(CACHE_KEY, StringCodec.INSTANCE).delete();
        fundNetWorthMapper.deleteByQuery(QueryWrapper.create().eq("fundCode", CODE));
    }

    /**
     * 用例结束后清掉降级缓存，避免污染真实 Redis
     */
    @AfterEach
    void tearDown() {
        redissonClient.getBucket(CACHE_KEY, StringCodec.INSTANCE).delete();
        fundNetWorthMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
    }

    /**
     * 季报只有前十大、中报是全量，两者合计差异符合预期，三个日期都能读到
     */
    @Test
    @DisplayName("季报与中报覆盖差异与三日期暴露")
    void quarterAndMidYearTotalsDiffer() {
        mockPingzhong();
        when(fundHoldingAdapter.fetchAll(eq(CODE), eq(HoldingKind.STOCK), any())).thenReturn(List.of(
                snapshotOf("2026Q1", LocalDate.of(2026, 3, 31), "16.91"),
                snapshotOf("2026Q2", LocalDate.of(2026, 6, 30), "94.27")));

        FundSnapshotVO quarterVo = fundSnapshotService.load(CODE, TODAY);

        assertThat(quarterVo.getHoldingPeriod()).isEqualTo("2026Q2");
        assertThat(quarterVo.getHoldingCutoffDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(quarterVo.getHoldingDisclosureDate()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(quarterVo.getNetWorthDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(quarterVo.getFullDisclosure()).isTrue();
        assertThat(quarterVo.getTotalStockWeight()).isEqualByComparingTo("94.27");
        assertThat(quarterVo.getStockRatio()).isEqualByComparingTo("94.33");
        assertThat(quarterVo.getEstimable()).isTrue();
        assertThat(quarterVo.getDegraded()).isFalse();
        assertThat(quarterVo.getNetWorthPoints()).isNotEmpty();
        // 全量披露时重仓股合计应与季末股票仓位吻合到 0.1 个百分点内
        assertThat(quarterVo.getTotalStockWeight().subtract(quarterVo.getStockRatio()).abs())
                .isLessThan(new BigDecimal("0.1"));
    }

    /**
     * 净值增量落库，重复请求只补新点，不会重复插入已有日期
     */
    @Test
    @DisplayName("净值增量落库幂等且可按区间查回")
    void netWorthSavedIncrementally() {
        mockPingzhong();

        // 连取三次，只有第一次会写库，后两次库内最新日期已追平上游
        fundSnapshotService.load(CODE, TODAY);
        fundSnapshotService.load(CODE, TODAY);
        fundSnapshotService.load(CODE, TODAY);

        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        assertThat(fundNetWorthService.listRange(CODE, start, end)).hasSize(3);
        assertThat(fundNetWorthService.latestDate(CODE)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(fundNetWorthMapper.selectCountByQuery(QueryWrapper.create()
                .eq("fundCode", CODE).ge("netWorthDate", start).le("netWorthDate", end))).isEqualTo(3);
    }

    /**
     * 增量写入只补比库内最大值更新的点，已有的历史行不动
     */
    @Test
    @DisplayName("增量写入只补新点")
    void incrementalSkipsExistingDates() {
        List<FundNetWorthPoint> firstBatch = List.of(
                new FundNetWorthPoint(LocalDate.of(2026, 10, 21), new BigDecimal("1.1000"), new BigDecimal("0.10")),
                new FundNetWorthPoint(LocalDate.of(2026, 10, 22), new BigDecimal("1.1100"), new BigDecimal("0.90")));
        List<FundNetWorthPoint> secondBatch = List.of(
                new FundNetWorthPoint(LocalDate.of(2026, 10, 22), new BigDecimal("9.9999"), new BigDecimal("9.99")),
                new FundNetWorthPoint(LocalDate.of(2026, 10, 23), new BigDecimal("1.1200"), new BigDecimal("0.90")));

        assertThat(fundNetWorthService.saveIncremental(CODE, firstBatch)).isEqualTo(2);
        assertThat(fundNetWorthService.saveIncremental(CODE, secondBatch)).isEqualTo(1);
        assertThat(fundNetWorthService.listRange(CODE, null, null)).hasSize(3);
        // 已有日期按原值保留，不做历史覆盖
        assertThat(fundNetWorthService.listRange(CODE, null, null).get(1).getUnitNetWorth())
                .isEqualByComparingTo("1.1100");
    }

    /**
     * 上游结构变化时抛出可识别异常，且该基金明确不可预估
     */
    @Test
    @DisplayName("上游改坏后抛具名异常并判定不可预估")
    void brokenUpstreamThrows() {
        when(fundPingzhongDataAdapter.fetch(CODE)).thenThrow(
                new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "pingzhongdata 无净值序列"));

        assertThatThrownBy(() -> fundSnapshotService.load(CODE, TODAY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(HttpsCodeEnum.DATA_SOURCE_BROKEN.getCode()))
                .hasMessageContaining("无净值序列");
    }

    /**
     * 首次成功后有缓存，随后断网能返回上次成功结果并带出数据截止时间
     */
    @Test
    @DisplayName("断网回退上次成功缓存并带出截止时间")
    void fallbackToCachedSnapshot() {
        mockPingzhong();
        FundSnapshotVO fresh = fundSnapshotService.load(CODE, TODAY);
        assertThat(fresh.getDegraded()).isFalse();

        when(fundPingzhongDataAdapter.fetch(CODE)).thenThrow(
                new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "外部数据源请求失败，请稍后重试"));

        FundSnapshotVO fallback = fundSnapshotService.load(CODE, TODAY);

        assertThat(fallback.getDegraded()).isTrue();
        assertThat(fallback.getDataCutoffDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(fallback.getNetWorthDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(fallback.getTotalStockWeight()).isEqualByComparingTo("94.27");
        assertThat(fallback.getEstimable()).isTrue();
    }

    /**
     * 无持仓的基金能说明不可预估的原因，而不是返回空对象
     */
    @Test
    @DisplayName("无持仓时给出不可预估原因")
    void noHoldingGivesReason() {
        mockPingzhong();
        when(fundHoldingAdapter.fetchAll(eq(CODE), eq(HoldingKind.STOCK), any())).thenReturn(List.of());

        FundSnapshotVO vo = fundSnapshotService.load(CODE, TODAY);

        assertThat(vo.getEstimable()).isFalse();
        assertThat(vo.getUnavailableReason()).contains("无已披露的股票持仓");
        assertThat(vo.getStockHoldings()).isEmpty();
    }

    /**
     * 固定净值与季末仓位
     */
    private void mockPingzhong() {
        FundPingzhongData data = new FundPingzhongData();
        data.setCode(CODE);
        data.setName("大成中证红利指数A");
        data.setNetWorthPoints(List.of(
                new FundNetWorthPoint(LocalDate.of(2026, 10, 22), new BigDecimal("1.1329"), new BigDecimal("-0.02")),
                new FundNetWorthPoint(LocalDate.of(2026, 10, 23), new BigDecimal("1.1328"), new BigDecimal("-0.01")),
                new FundNetWorthPoint(LocalDate.of(2026, 9, 30), new BigDecimal("1.1300"), new BigDecimal("0.30"))));
        data.setAssetAllocations(List.of(
                new FundAssetAllocationPoint(LocalDate.of(2026, 6, 30),
                        new BigDecimal("94.33"), BigDecimal.ZERO, new BigDecimal("5.79"))));
        when(fundPingzhongDataAdapter.fetch(CODE)).thenReturn(data);
        when(fundHoldingAdapter.fetchAll(eq(CODE), eq(HoldingKind.STOCK), any())).thenReturn(List.of(
                snapshotOf("2026Q2", LocalDate.of(2026, 6, 30), "94.27")));
    }

    /**
     * 拼一份持仓快照，权重拆成两行且合计等于给定量
     *
     * @param period   报告期
     * @param cutoff   截止日
     * @param totalWeight 合计权重
     * @return 快照
     */
    private FundHoldingSnapshot snapshotOf(String period, LocalDate cutoff, String totalWeight) {
        BigDecimal total = new BigDecimal(totalWeight);
        BigDecimal first = total.multiply(new BigDecimal("0.6")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal second = total.subtract(first);
        return new FundHoldingSnapshot(period, cutoff, HoldingKind.STOCK, List.of(
                new FundHoldingItem("601919", "中远海控", first),
                new FundHoldingItem("600256", "广汇能源", second)));
    }
}
