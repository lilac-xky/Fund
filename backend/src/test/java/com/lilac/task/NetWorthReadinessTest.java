package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.domain.vo.EstimateSnapshotVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.engine.model.NetWorthReadiness;
import com.lilac.service.EstimateSnapshotService;
import com.lilac.service.FundNetWorthService;
import com.lilac.service.VarietyService;
import com.lilac.service.impl.NetWorthReadinessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 晚间净值就绪判定测试，重点是「只等该等的基金」与「到截止时刻必须发」
 */
class NetWorthReadinessTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 19);

    private EstimateSnapshotService estimateSnapshotService;

    private FundNetWorthService fundNetWorthService;

    private VarietyService varietyService;

    private NetWorthReadinessServiceImpl readinessService;

    /**
     * 三件依赖全部用桩，判定逻辑单独验证
     */
    @BeforeEach
    void setUp() {
        estimateSnapshotService = mock(EstimateSnapshotService.class);
        fundNetWorthService = mock(FundNetWorthService.class);
        varietyService = mock(VarietyService.class);
        readinessService = new NetWorthReadinessServiceImpl();
        ReflectionTestUtils.setField(readinessService, "estimateSnapshotService", estimateSnapshotService);
        ReflectionTestUtils.setField(readinessService, "fundNetWorthService", fundNetWorthService);
        ReflectionTestUtils.setField(readinessService, "varietyService", varietyService);
        ReflectionTestUtils.setField(readinessService, "taskProperties", propertiesWithDeadline("23:00"));
    }

    /**
     * 净值全部到齐时可以发信，不需要等待
     */
    @Test
    @DisplayName("净值到齐就绪且不等待")
    void readyWhenAllArrived() {
        givenSnapshots(snapshot("OF:000922", true, "000922"));
        givenVarieties(variety("OF:000922", "000922", "中证红利ETF联接A"));
        when(fundNetWorthService.latestDate("000922")).thenReturn(TRADE_DATE);

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 20, 0));

        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.isShouldWait()).isFalse();
        assertThat(readiness.getExpectedCount()).isEqualTo(1);
        assertThat(readiness.getPending()).isEmpty();
    }

    /**
     * 有净值还没出时必须等，并把缺的那只列出来
     */
    @Test
    @DisplayName("净值未齐时等待并列出缺口")
    void waitsWhenNetWorthMissing() {
        givenSnapshots(snapshot("OF:000922", true, "000922"));
        givenVarieties(variety("OF:000922", "000922", "中证红利ETF联接A"));
        when(fundNetWorthService.latestDate("000922")).thenReturn(TRADE_DATE.minusDays(1));

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 20, 0));

        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.isShouldWait()).isTrue();
        assertThat(readiness.getPending()).hasSize(1);
        assertThat(readiness.getPending().get(0).getCode()).isEqualTo("000922");
        assertThat(readiness.getNote()).contains("还差 1 只");
    }

    /**
     * 无法预估的基金不参与等待：QDII 这类本来就不会有预估值，等它只会让整封邮件永远发不出去
     */
    @Test
    @DisplayName("无法预估的基金不阻塞发信")
    void unevaluableDoesNotBlock() {
        // 摩根标普500 这类 QDII 当日 evaluable=false，没有任何可对照输出
        givenSnapshots(snapshot("OF:017641", false, "017641"));
        givenVarieties(variety("OF:017641", "017641", "摩根标普500指数(QDII)人民币A"));
        when(fundNetWorthService.latestDate(any())).thenReturn(null);

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 20, 0));

        assertThat(readiness.getExpectedCount()).isZero();
        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.isShouldWait()).isFalse();
    }

    /**
     * 只有可预估的基金参与等待，无法预估的同时存在也不影响结论
     */
    @Test
    @DisplayName("可预估与不可预估混排时只等可预估的那只")
    void onlyEvaluableCounts() {
        givenSnapshots(snapshot("OF:000922", true, "000922"), snapshot("OF:017641", false, "017641"));
        givenVarieties(variety("OF:000922", "000922", "中证红利ETF联接A"),
                variety("OF:017641", "017641", "摩根标普500指数(QDII)人民币A"));
        when(fundNetWorthService.latestDate("000922")).thenReturn(TRADE_DATE);
        when(fundNetWorthService.latestDate("017641")).thenReturn(null);

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 20, 0));

        assertThat(readiness.getExpectedCount()).isEqualTo(1);
        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.getPending()).isEmpty();
    }

    /**
     * 到截止时刻仍未齐也必须发，否则一只长期停牌的基金会让当晚彻底没有邮件
     */
    @Test
    @DisplayName("到截止时刻按缺口发送")
    void sendsAtDeadline() {
        givenSnapshots(snapshot("OF:000922", true, "000922"));
        givenVarieties(variety("OF:000922", "000922", "中证红利ETF联接A"));
        when(fundNetWorthService.latestDate("000922")).thenReturn(TRADE_DATE.minusDays(1));

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 23, 0));

        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.isShouldWait()).isFalse();
        assertThat(readiness.getPending()).hasSize(1);
        assertThat(readiness.getNote()).contains("已过截止时刻").contains("按当前缺口发送");
    }

    /**
     * 截止时刻之前仍然等待，不能提前放弃
     */
    @Test
    @DisplayName("截止时刻前仍继续等待")
    void stillWaitsBeforeDeadline() {
        givenSnapshots(snapshot("OF:000922", true, "000922"));
        givenVarieties(variety("OF:000922", "000922", "中证红利ETF联接A"));
        when(fundNetWorthService.latestDate("000922")).thenReturn(TRADE_DATE.minusDays(1));

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 22, 59));

        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.isShouldWait()).isTrue();
    }

    /**
     * 当日没有任何可对照预估时直接发，不必空等
     */
    @Test
    @DisplayName("无可对照预估时直接发出")
    void noSnapshotSendsDirectly() {
        givenSnapshots();

        NetWorthReadiness readiness = readinessService.check(TRADE_DATE, LocalDateTime.of(2026, 11, 19, 20, 0));

        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.isShouldWait()).isFalse();
        assertThat(readiness.getNote()).contains("没有可对照的预估");
    }

    /**
     * 造一份配置，只设置晚间截止时刻
     *
     * @param deadline 截止时刻
     * @return 配置
     */
    private TaskProperties propertiesWithDeadline(String deadline) {
        TaskProperties properties = new TaskProperties();
        TaskProperties.PointProperties evening = new TaskProperties.PointProperties();
        evening.setDeadline(deadline);
        Map<String, TaskProperties.PointProperties> points = new LinkedHashMap<>();
        points.put("EVENING", evening);
        properties.setPoints(points);
        return properties;
    }

    /**
     * 桩一份当日留存
     *
     * @param snapshots 留存
     */
    private void givenSnapshots(EstimateSnapshotVO... snapshots) {
        when(estimateSnapshotService.listByDate(any())).thenReturn(List.of(snapshots));
    }

    /**
     * 桩一份品种全集
     *
     * @param varieties 品种
     */
    private void givenVarieties(VarietyVO... varieties) {
        when(varietyService.listVariety(any())).thenReturn(List.of(varieties));
    }

    /**
     * 造一份预估留存
     *
     * @param varietyKey 品种主键
     * @param evaluable  当时是否给出可对照输出
     * @param code       6 位代码，仅用于标识
     * @return 留存
     */
    private EstimateSnapshotVO snapshot(String varietyKey, boolean evaluable, String code) {
        EstimateSnapshotVO snapshot = new EstimateSnapshotVO();
        snapshot.setVarietyKey(varietyKey);
        snapshot.setTradeDate(TRADE_DATE);
        snapshot.setEvaluable(evaluable);
        snapshot.setUnavailableReason(evaluable ? null : "QDII 跟踪境外指数，无可用代理，无法预估(" + code + ")");
        return snapshot;
    }

    /**
     * 造一只品种
     *
     * @param varietyKey 品种主键
     * @param code       6 位代码
     * @param name       名称
     * @return 品种
     */
    private VarietyVO variety(String varietyKey, String code, String name) {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey(varietyKey);
        variety.setVarietyCode(code);
        variety.setVarietyName(name);
        return variety;
    }
}
