package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.domain.vo.TaskOverviewVO;
import com.lilac.domain.vo.TaskPointStatusVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.SendRecordMapper;
import com.lilac.service.SendRecordService;
import com.lilac.task.model.PointOutcome;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 时点调度验收测试，留痕走真实数据库，邮件与产出用桩隔离
 */
@SpringBootTest
@Transactional
class TaskOrchestratorIntegrationTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);
    private static final LocalDateTime PRE_CLOSE_DUE = LocalDateTime.of(2026, 10, 30, 14, 30);

    @Autowired
    private TaskOrchestrator orchestrator;

    @Autowired
    private TaskProperties taskProperties;

    @Autowired
    private SendRecordService sendRecordService;

    @Autowired
    private SendRecordMapper sendRecordMapper;

    @MockitoBean
    private TaskOutcomeDispatcher taskOutcomeDispatcher;

    @MockitoBean
    private ClockGuard clockGuard;

    /**
     * 清空留痕、恢复开关，并让产出默认成功、时钟默认正常，用例自己决定何时触发
     */
    @BeforeEach
    void setUp() {
        sendRecordMapper.deleteByQuery(QueryWrapper.create().where("1 = 1"));
        taskProperties.setEnabled(true);
        taskProperties.setSchedulerEnabled(true);
        Mockito.when(taskOutcomeDispatcher.dispatch(ArgumentMatchers.any(SendPoint.class),
                        ArgumentMatchers.any(LocalDate.class), ArgumentMatchers.anyBoolean()))
                .thenReturn(PointOutcome.ok("产出成功"));
        Mockito.when(clockGuard.inGuardWindow(ArgumentMatchers.any())).thenReturn(false);
        Mockito.when(clockGuard.sample(ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
    }

    /**
     * 手动触发后留痕落到「已补发」，页面能查到该时点已发送
     */
    @Test
    @DisplayName("手动触发后页面能查到该时点已发送")
    void manualTriggerLeavesRecord() {
        orchestrator.triggerManually(SendPoint.PRE_CLOSE, FRIDAY, false);

        TaskOverviewVO overview = orchestrator.overview(FRIDAY);
        TaskPointStatusVO preClose = overview.getItems().stream()
                .filter(item -> item.getPoint() == SendPoint.PRE_CLOSE)
                .findFirst().orElseThrow();
        assertThat(preClose.getStatus()).isEqualTo(SendStatus.RESENT);
        assertThat(preClose.getStatusLabel()).isEqualTo("已补发");
        assertThat(preClose.getSentTime()).isNotNull();
        assertThat(preClose.getOverdue()).isFalse();
        assertThat(overview.getSentCount()).isEqualTo(1);
    }

    /**
     * 产出失败时留痕记失败并带原因，页面标「未送达」
     */
    @Test
    @DisplayName("产出失败时留痕记失败并带原因")
    void failureLeavesReason() {
        Mockito.when(taskOutcomeDispatcher.dispatch(SendPoint.PRE_CLOSE, FRIDAY, false))
                .thenReturn(PointOutcome.fail("SMTP 认证失败"));
        orchestrator.triggerManually(SendPoint.PRE_CLOSE, FRIDAY, false);

        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(FRIDAY);
        List<SendRecordVO> rows = sendRecordService.listSendRecord(query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(rows.get(0).getFailReason()).isEqualTo("SMTP 认证失败");
        assertThat(rows.get(0).getSentTime()).isNull();
    }

    /**
     * 同一时点重复触发不新增行，靠「日期 + 时点」唯一索引覆盖原记录
     */
    @Test
    @DisplayName("同一时点重复触发只保留一条留痕")
    void repeatedTriggerKeepsSingleRow() {
        orchestrator.triggerManually(SendPoint.PRE_CLOSE, FRIDAY, false);
        orchestrator.triggerManually(SendPoint.PRE_CLOSE, FRIDAY, false);

        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(FRIDAY);
        assertThat(sendRecordService.listSendRecord(query)).hasSize(1);
    }

    /**
     * 跨过时点后再触发，补跑把当日缺口补上，这是本条最核心的验收点
     */
    @Test
    @DisplayName("跨过时点后补跑生效")
    void catchUpAfterMissedPoint() {
        LocalDateTime afterDue = PRE_CLOSE_DUE.plusMinutes(5);
        int count = orchestrator.runCatchUp(FRIDAY, afterDue);

        assertThat(count).isEqualTo(1);
        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(FRIDAY);
        List<SendRecordVO> rows = sendRecordService.listSendRecord(query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSendPoint()).isEqualTo(SendPoint.PRE_CLOSE);
        assertThat(rows.get(0).getStatus()).isEqualTo(SendStatus.RESENT);
    }

    /**
     * 已补过的时点再跑一次补跑不再重复发送
     */
    @Test
    @DisplayName("补跑不会重复发送已成功的时点")
    void catchUpIsIdempotent() {
        LocalDateTime afterDue = PRE_CLOSE_DUE.plusMinutes(5);
        assertThat(orchestrator.runCatchUp(FRIDAY, afterDue)).isEqualTo(1);
        assertThat(orchestrator.runCatchUp(FRIDAY, afterDue)).isZero();
    }

    /**
     * 超过补跑时效窗口的漏跑不再补发，避免收盘后补一封过期提醒
     */
    @Test
    @DisplayName("超过补跑窗口的时点不再补发")
    void stalePointNotCaughtUp() {
        LocalDateTime wayLate = PRE_CLOSE_DUE.plusMinutes(taskProperties.getCatchUpWindowMinutes() + 1);
        assertThat(orchestrator.runCatchUp(FRIDAY, wayLate)).isZero();
    }

    /**
     * 非交易日补跑不产出，手动触发也被拒绝
     */
    @Test
    @DisplayName("非交易日既不补跑也不允许手动触发")
    void nonTradingDayRejected() {
        LocalDate saturday = LocalDate.of(2026, 10, 31);
        assertThat(orchestrator.runCatchUp(saturday, LocalDateTime.of(2026, 10, 31, 14, 35))).isZero();
        assertThatThrownBy(() -> orchestrator.triggerManually(SendPoint.PRE_CLOSE, saturday, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode()));
    }

    /**
     * 时钟跳变后触发被跳过，但留痕里写明原因，页面上能看出「这一封为什么没发」
     */
    @Test
    @DisplayName("时钟跳变后当次触发被跳过并留下原因")
    void clockJumpSkipsTriggerWithReason() {
        Mockito.when(clockGuard.sample(ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(new ClockGuard.ClockJump(3600, true)));
        orchestrator.fireAt(SendPoint.PRE_CLOSE, PRE_CLOSE_DUE);

        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(FRIDAY);
        List<SendRecordVO> rows = sendRecordService.listSendRecord(query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(rows.get(0).getFailReason()).contains("时钟").contains("3600");
        Mockito.verify(taskOutcomeDispatcher, Mockito.never())
                .dispatch(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }

    /**
     * 定时触发路径在交易日的应发时刻执行一次，并记为「已发送」而不是「已补发」
     */
    @Test
    @DisplayName("定时到点触发的留痕记为已发送")
    void scheduledTriggerMarksSent() {
        orchestrator.fireAt(SendPoint.PRE_CLOSE, PRE_CLOSE_DUE);

        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(FRIDAY);
        List<SendRecordVO> rows = sendRecordService.listSendRecord(query);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(SendStatus.SENT);
        assertThat(rows.get(0).getSentTime()).isNotNull();
    }

    /**
     * 非交易日到点触发不产出，也不留下任何留痕
     */
    @Test
    @DisplayName("非交易日到点触发不产出")
    void scheduledTriggerSkippedOnWeekend() {
        orchestrator.fireAt(SendPoint.PRE_CLOSE, LocalDateTime.of(2026, 10, 31, 14, 30));

        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(LocalDate.of(2026, 10, 31));
        assertThat(sendRecordService.listSendRecord(query)).isEmpty();
    }

    /**
     * 总览里非交易日给出休市说明，不把四个时点全显示成失败
     */
    @Test
    @DisplayName("非交易日总览给出休市说明")
    void overviewExplainsNonTradingDay() {
        TaskOverviewVO overview = orchestrator.overview(LocalDate.of(2026, 10, 31));
        assertThat(overview.getTradingDay()).isFalse();
        assertThat(overview.getTradingDayNote()).contains("休市");
        assertThat(overview.getItems()).hasSize(4);
        assertThat(overview.getItems()).allSatisfy(item -> assertThat(item.getStatus()).isNull());
    }
}
