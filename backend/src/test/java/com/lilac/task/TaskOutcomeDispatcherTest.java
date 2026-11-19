package com.lilac.task;

import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.enums.SendPoint;
import com.lilac.service.EstimateCalibrationService;
import com.lilac.service.EstimateService;
import com.lilac.task.model.PointOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 时点产出分派测试，核心是四个时点都必须真的发信，而不只是刷新数据
 */
class TaskOutcomeDispatcherTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 19);

    private EstimateService estimateService;

    private EstimateCalibrationService calibrationService;

    /** 记录每个时点是否真的走到了邮件出口 */
    private final List<SendPoint> sentPoints = new ArrayList<>();

    private TaskOutcomeDispatcher dispatcher;

    /**
     * 预估与校准各给一份最小可用结果，邮件出口记录调用过哪些时点
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        estimateService = mock(EstimateService.class);
        calibrationService = mock(EstimateCalibrationService.class);
        EstimateBatchVO estimate = new EstimateBatchVO();
        estimate.setRequested(2);
        estimate.setEstimableCount(2);
        estimate.setUnavailableCount(0);
        when(estimateService.estimateBatch(any())).thenReturn(estimate);
        CalibrationResultVO calibration = new CalibrationResultVO();
        calibration.setCalibratedCount(2);
        calibration.setOverThresholdCount(0);
        calibration.setGapCount(0);
        when(calibrationService.calibrate(any())).thenReturn(calibration);

        ReminderMailPort port = (point, tradeDate, batch, result, resend) -> {
            sentPoints.add(point);
            return "mail/" + tradeDate + "-" + point.name().toLowerCase() + "-sent-000000.txt";
        };
        ObjectProvider<ReminderMailPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(port);
        dispatcher = new TaskOutcomeDispatcher(estimateService, calibrationService, provider);
    }

    /**
     * 四个时点逐个分派都必须发信，这是本次修正的核心：此前只有收盘前会发
     */
    @Test
    @DisplayName("四个时点都应真正发出邮件")
    void everyPointSends() {
        for (SendPoint point : SendPoint.values()) {
            PointOutcome outcome = dispatcher.dispatch(point, TRADE_DATE, false);
            assertThat(outcome.isSuccess()).as(point + " 应发送成功").isTrue();
            assertThat(outcome.getContentPath()).as(point + " 应带出留存路径").isNotNull();
        }
        assertThat(sentPoints).containsExactlyInAnyOrder(SendPoint.MORNING, SendPoint.NOON,
                SendPoint.PRE_CLOSE, SendPoint.EVENING);
    }

    /**
     * 晚间不讲当日预估，邮件出口拿到的预估必须为空，防止正文把白天数字再发一遍
     */
    @Test
    @DisplayName("晚间不发预估只发校准结果")
    void eveningPassesCalibrationOnly() {
        List<Object[]> seen = new ArrayList<>();
        @SuppressWarnings("unchecked")
        ObjectProvider<ReminderMailPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn((point, tradeDate, estimate, calibration, resend) -> {
            seen.add(new Object[]{estimate, calibration});
            return "mail/x.txt";
        });
        new TaskOutcomeDispatcher(estimateService, calibrationService, provider)
                .dispatch(SendPoint.EVENING, TRADE_DATE, false);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0)[0]).isNull();
        assertThat(seen.get(0)[1]).isNotNull();
    }

    /**
     * 取预估抛异常时仍要发信，正文按「今日无法预估」渲染，绝不静默不发
     */
    @Test
    @DisplayName("预估取不到时仍发出邮件并标注无法预估")
    void estimateFailureStillSends() {
        when(estimateService.estimateBatch(any())).thenThrow(new IllegalStateException("尚未录入持仓"));
        PointOutcome outcome = dispatcher.dispatch(SendPoint.MORNING, TRADE_DATE, false);

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(sentPoints).containsExactly(SendPoint.MORNING);
        assertThat(outcome.getDetail()).contains("今日无法预估");
    }

    /**
     * 邮件出口未接入时该时点记失败并说明原因，不能假装成功
     */
    @Test
    @DisplayName("邮件出口未接入时记失败")
    @SuppressWarnings("unchecked")
    void missingPortFails() {
        ObjectProvider<ReminderMailPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PointOutcome outcome = new TaskOutcomeDispatcher(estimateService, calibrationService, provider)
                .dispatch(SendPoint.NOON, TRADE_DATE, false);

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.getFailReason()).contains("未接入");
    }

    /**
     * 定时触发与补发的摘要要能区分，页面据此判断这封是不是补的
     */
    @Test
    @DisplayName("补发时摘要写明含补发")
    void resendDetailMarked() {
        PointOutcome outcome = dispatcher.dispatch(SendPoint.PRE_CLOSE, TRADE_DATE, true);
        assertThat(outcome.getDetail()).contains("含补发");
    }

    /**
     * 校准返回空时该时点记失败，不发出没有正文内容的邮件
     */
    @Test
    @DisplayName("校准无结果时记失败")
    void emptyCalibrationFails() {
        when(calibrationService.calibrate(any())).thenReturn(null);
        PointOutcome outcome = dispatcher.dispatch(SendPoint.EVENING, TRADE_DATE, false);

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(sentPoints).isEmpty();
    }
}
