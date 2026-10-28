package com.lilac.strategy;

import com.lilac.adapter.csindex.CsIndexIndicatorAdapter;
import com.lilac.adapter.model.IndexDividendPoint;
import com.lilac.adapter.model.IndexDividendSnapshot;
import com.lilac.domain.dto.dividend.DividendManualRequest;
import com.lilac.domain.dto.dividend.DividendQueryRequest;
import com.lilac.domain.dto.variety.VarietySaveRequest;
import com.lilac.domain.entity.IndexDividend;
import com.lilac.domain.vo.IndexDividendOverviewVO;
import com.lilac.domain.vo.IndexDividendRefreshVO;
import com.lilac.enums.DividendSource;
import com.lilac.enums.FundCategory;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.VarietyType;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.IndexDividendMapper;
import com.lilac.service.IndexDividendService;
import com.lilac.service.VarietyService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 股息率验收测试，中证官网用桩隔离，数据库走真实实例
 */
@SpringBootTest
@Transactional
class IndexDividendIntegrationTest {

    private static final String DIVIDEND_INDEX = "000922";
    private static final String GUOZHENG_INDEX = "399321";
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private IndexDividendService indexDividendService;

    @Autowired
    private VarietyService varietyService;

    @Autowired
    private IndexDividendMapper indexDividendMapper;

    @MockitoBean
    private CsIndexIndicatorAdapter csIndexIndicatorAdapter;

    /**
     * 清掉股息率数据，避免与上一次用例串数据
     */
    @BeforeEach
    void setUp() {
        indexDividendMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
    }

    /**
     * 自动抓取写入的数据带数据日期与来源，页面可取用
     */
    @Test
    @DisplayName("自动抓取落库并带出数据日期")
    void autoFetchSavesWithDataDate() {
        when(csIndexIndicatorAdapter.fetch(DIVIDEND_INDEX)).thenReturn(snapshotOf(DIVIDEND_INDEX, "中证红利指数",
                TODAY.minusDays(1), "4.08", "4.23"));

        IndexDividendRefreshVO result = indexDividendService.refresh(List.of(DIVIDEND_INDEX));

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getManualRequiredCount()).isZero();
        IndexDividendOverviewVO overview = indexDividendService.listOverview(byIndex(DIVIDEND_INDEX)).get(0);
        assertThat(overview.getDataDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(overview.getDividendYield()).isEqualByComparingTo("4.23");
        assertThat(overview.getSourceLabel()).isEqualTo("自动抓取");
        assertThat(overview.getAutoAvailable()).isTrue();
        assertThat(overview.getStale()).isFalse();
    }

    /**
     * 国证系列抓取不到时不崩溃，落到手工覆盖路径并给出可读原因
     */
    @Test
    @DisplayName("399321 抓取失败落到手工覆盖路径")
    void unavailableIndexFallsBackToManual() {
        when(csIndexIndicatorAdapter.fetch(GUOZHENG_INDEX)).thenReturn(null);

        IndexDividendRefreshVO result = indexDividendService.refresh(List.of(GUOZHENG_INDEX));

        assertThat(result.getSavedCount()).isZero();
        assertThat(result.getManualRequiredCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("未收录").contains("手工覆盖");
        assertThat(indexDividendMapper.selectCountByQuery(QueryWrapper.create())).isZero();
    }

    /**
     * 手工填入的股息率能覆盖同日的自动抓取结果，且同样带日期与手工来源
     */
    @Test
    @DisplayName("手工值覆盖同日自动抓取结果")
    void manualOverridesAuto() {
        when(csIndexIndicatorAdapter.fetch(GUOZHENG_INDEX))
                .thenReturn(snapshotOf(GUOZHENG_INDEX, "国证红利", TODAY, "3.10", "3.20"));
        indexDividendService.refresh(List.of(GUOZHENG_INDEX));

        indexDividendService.saveManual(manualRequestOf(GUOZHENG_INDEX, TODAY, "5.55"));

        IndexDividendOverviewVO overview = indexDividendService.listOverview(byIndex(GUOZHENG_INDEX)).get(0);
        assertThat(overview.getDividendYield()).isEqualByComparingTo("5.55");
        assertThat(overview.getDataDate()).isEqualTo(TODAY);
        assertThat(overview.getSourceLabel()).isEqualTo("手工覆盖");
        assertThat(overview.getAutoAvailable()).isFalse();
        // 同一指数同一日期只留一行，手工值改写原行而不是再插一行
        assertThat(indexDividendMapper.selectCountByQuery(QueryWrapper.create()
                .eq(IndexDividend::getIndexCode, GUOZHENG_INDEX))).isEqualTo(1);
    }

    /**
     * 不同日期各自保留一行，手工录入不会抹掉历史序列
     */
    @Test
    @DisplayName("不同日期各自保留一行")
    void manualOnOtherDateKeepsHistory() {
        when(csIndexIndicatorAdapter.fetch(GUOZHENG_INDEX))
                .thenReturn(snapshotOf(GUOZHENG_INDEX, "国证红利", TODAY.minusDays(2), "3.10", "3.20"));
        indexDividendService.refresh(List.of(GUOZHENG_INDEX));

        indexDividendService.saveManual(manualRequestOf(GUOZHENG_INDEX, TODAY, "5.55"));

        IndexDividendOverviewVO overview = indexDividendService.listOverview(byIndex(GUOZHENG_INDEX)).get(0);
        assertThat(overview.getDividendYield()).isEqualByComparingTo("5.55");
        assertThat(overview.getHistory()).hasSize(2);
        assertThat(indexDividendMapper.selectCountByQuery(QueryWrapper.create()
                .eq(IndexDividend::getIndexCode, GUOZHENG_INDEX))).isEqualTo(2);
    }

    /**
     * 自动抓取不得覆盖已经手工录入的同一日期
     */
    @Test
    @DisplayName("自动抓取不覆盖手工值")
    void autoFetchKeepsManualValue() {
        indexDividendService.saveManual(manualRequestOf(DIVIDEND_INDEX, TODAY.minusDays(1), "9.99"));
        when(csIndexIndicatorAdapter.fetch(DIVIDEND_INDEX)).thenReturn(snapshotOf(DIVIDEND_INDEX, "中证红利指数",
                TODAY.minusDays(1), "4.08", "4.23"));

        indexDividendService.refresh(List.of(DIVIDEND_INDEX));

        IndexDividend row = indexDividendMapper.selectOneByQuery(QueryWrapper.create()
                .eq(IndexDividend::getIndexCode, DIVIDEND_INDEX)
                .eq(IndexDividend::getDataDate, TODAY.minusDays(1)));
        assertThat(row.getDividendYield()).isEqualByComparingTo("9.99");
        assertThat(row.getSource()).isEqualTo(DividendSource.MANUAL);
    }

    /**
     * 数据日期超过阈值时标为过期并给出原因，供策略拒绝给出动作
     */
    @Test
    @DisplayName("超过阈值标为数据过期")
    void staleDataIsMarked() {
        indexDividendService.saveManual(manualRequestOf(DIVIDEND_INDEX, TODAY.minusDays(30), "4.08"));

        IndexDividendOverviewVO overview = indexDividendService.listOverview(byIndex(DIVIDEND_INDEX)).get(0);

        assertThat(overview.getStale()).isTrue();
        assertThat(overview.getAgeDays()).isEqualTo(30);
        assertThat(overview.getStaleReason()).contains("已过 30 天").contains("不得据此给出加减仓动作");
    }

    /**
     * 未显式指定指数时按库内红利持仓的跟踪指数推导刷新目标
     */
    @Test
    @DisplayName("留空刷新目标按红利持仓推导")
    void refreshTargetsFollowDividendHoldings() {
        varietyService.saveVariety(dividendVariety("OF:990922", "990922", "中证红利指数基金A", DIVIDEND_INDEX));
        when(csIndexIndicatorAdapter.fetch(DIVIDEND_INDEX)).thenReturn(snapshotOf(DIVIDEND_INDEX, "中证红利指数",
                TODAY.minusDays(1), "4.08", "4.23"));

        IndexDividendRefreshVO result = indexDividendService.refresh(null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getIndexCode()).isEqualTo(DIVIDEND_INDEX);
        assertThat(result.getSavedCount()).isEqualTo(1);
    }

    /**
     * 查询时可按基金业务主键取到其跟踪指数的股息率
     */
    @Test
    @DisplayName("按基金业务主键查到跟踪指数的股息率")
    void queryByVarietyKey() {
        varietyService.saveVariety(dividendVariety("OF:990922", "990922", "中证红利指数基金A", DIVIDEND_INDEX));
        indexDividendService.saveManual(manualRequestOf(DIVIDEND_INDEX, TODAY.minusDays(1), "4.23"));

        DividendQueryRequest request = new DividendQueryRequest();
        request.setVarietyKey("OF:990922");
        List<IndexDividendOverviewVO> rows = indexDividendService.listOverview(request);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDividendYield()).isEqualByComparingTo("4.23");
        assertThat(rows.get(0).getFundCodes()).contains("990922");
    }

    /**
     * 缺日期、日期越界、股息率越界都拒绝保存并指出字段
     */
    @Test
    @DisplayName("非法手工录入被拒绝并指出字段")
    void invalidManualInputRejected() {
        assertThatThrownBy(() -> indexDividendService.saveManual(manualRequestOf(DIVIDEND_INDEX, null, "4.08")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据日期");
        assertThatThrownBy(() -> indexDividendService.saveManual(
                manualRequestOf(DIVIDEND_INDEX, TODAY.plusDays(1), "4.08")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能晚于今天");
        assertThatThrownBy(() -> indexDividendService.saveManual(manualRequestOf(DIVIDEND_INDEX, TODAY, "-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0~100");
        assertThatThrownBy(() -> {
            DividendManualRequest request = manualRequestOf(DIVIDEND_INDEX, TODAY, "4.08");
            request.setIndexCode("922");
            indexDividendService.saveManual(request);
        }).isInstanceOf(BusinessException.class).hasMessageContaining("6 位字母数字");
        assertThat(indexDividendMapper.selectCountByQuery(QueryWrapper.create())).isZero();
    }

    /**
     * 上游结构变化时整批不中断，其余指数照常写入
     */
    @Test
    @DisplayName("单只抓取异常不影响其他指数")
    void oneFailureDoesNotBreakBatch() {
        when(csIndexIndicatorAdapter.fetch(GUOZHENG_INDEX))
                .thenThrow(new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "结构变化"));
        when(csIndexIndicatorAdapter.fetch(DIVIDEND_INDEX)).thenReturn(snapshotOf(DIVIDEND_INDEX, "中证红利指数",
                TODAY.minusDays(1), "4.08", "4.23"));

        IndexDividendRefreshVO result = indexDividendService.refresh(List.of(GUOZHENG_INDEX, DIVIDEND_INDEX));

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getManualRequiredCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getMessage()).contains("结构变化");
    }

    /**
     * 构造按指数查询的请求
     *
     * @param indexCode 指数代码
     * @return 查询请求
     */
    private DividendQueryRequest byIndex(String indexCode) {
        DividendQueryRequest request = new DividendQueryRequest();
        request.setIndexCode(indexCode);
        return request;
    }

    /**
     * 构造手工录入请求
     *
     * @param indexCode 指数代码
     * @param dataDate  数据日期，可为 null
     * @param yield     股息率
     * @return 手工录入请求
     */
    private DividendManualRequest manualRequestOf(String indexCode, LocalDate dataDate, String yield) {
        DividendManualRequest request = new DividendManualRequest();
        request.setIndexCode(indexCode);
        request.setDataDate(dataDate);
        request.setDividendYield(new BigDecimal(yield));
        return request;
    }

    /**
     * 构造抓取快照
     *
     * @param indexCode   指数代码
     * @param indexName   指数名称
     * @param dataDate    数据日期
     * @param yield1      股息率1
     * @param yield2      股息率2
     * @return 快照
     */
    private IndexDividendSnapshot snapshotOf(String indexCode, String indexName, LocalDate dataDate,
                                             String yield1, String yield2) {
        IndexDividendPoint point = new IndexDividendPoint(indexCode, dataDate, indexName,
                new BigDecimal(yield1), new BigDecimal(yield2));
        IndexDividendSnapshot snapshot = new IndexDividendSnapshot();
        snapshot.setIndexCode(indexCode);
        snapshot.setIndexName(indexName);
        snapshot.setDataDate(dataDate);
        snapshot.setDividendYield(point.usableYield());
        snapshot.setPoints(List.of(point));
        return snapshot;
    }

    /**
     * 构造红利品种保存请求
     *
     * @param varietyKey     业务主键
     * @param code           6 位代码
     * @param name           名称
     * @param trackIndexCode 跟踪指数代码
     * @return 保存请求
     */
    private VarietySaveRequest dividendVariety(String varietyKey, String code, String name, String trackIndexCode) {
        VarietySaveRequest request = new VarietySaveRequest();
        request.setVarietyKey(varietyKey);
        request.setVarietyType(VarietyType.OF);
        request.setVarietyCode(code);
        request.setVarietyName(name);
        request.setCategory(FundCategory.DIVIDEND);
        request.setTrackIndexCode(trackIndexCode);
        request.setConfirmed(Boolean.TRUE);
        return request;
    }
}
