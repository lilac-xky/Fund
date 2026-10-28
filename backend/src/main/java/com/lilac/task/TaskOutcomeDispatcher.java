package com.lilac.task;

import com.lilac.domain.dto.estimate.CalibrationRequest;
import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.enums.SendPoint;
import com.lilac.service.EstimateCalibrationService;
import com.lilac.service.EstimateService;
import com.lilac.task.model.PointOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 各时点产出编排，早盘午盘刷预估、收盘前发邮件、晚间跑校准
 */
@Slf4j
@Component
public class TaskOutcomeDispatcher {

    private final EstimateService estimateService;

    private final EstimateCalibrationService estimateCalibrationService;

    private final ObjectProvider<CloseReminderPort> closeReminderPorts;

    /**
     * 注入四个时点所需的产出能力，邮件出口按需解析，未接入时只报失败不阻断启动
     *
     * @param estimateService            预估业务
     * @param estimateCalibrationService 晚间校准业务
     * @param closeReminderPorts         收盘前邮件出口，可能尚未接入
     */
    public TaskOutcomeDispatcher(EstimateService estimateService,
                                 EstimateCalibrationService estimateCalibrationService,
                                 ObjectProvider<CloseReminderPort> closeReminderPorts) {
        this.estimateService = estimateService;
        this.estimateCalibrationService = estimateCalibrationService;
        this.closeReminderPorts = closeReminderPorts;
    }

    /**
     * 按执行时点分派产出，异常统一转成失败结果交给留痕
     *
     * @param point     时点
     * @param tradeDate 交易日
     * @param resend    是否携带未送达内容的补发
     * @return 产出结果
     */
    public PointOutcome dispatch(SendPoint point, LocalDate tradeDate, boolean resend) {
        try {
            return switch (point) {
                case MORNING -> morning();
                case NOON -> noon();
                case PRE_CLOSE -> preClose(tradeDate, resend);
                case EVENING -> evening();
            };
        } catch (ReminderDeliveryException e) {
            // 发送失败时把留存路径一并带出，页面上才能告诉使用者「失败了，内容在哪」
            PointOutcome outcome = PointOutcome.fail(e.getMessage());
            outcome.setContentPath(e.getContentPath());
            return outcome;
        } catch (RuntimeException e) {
            log.error("时点产出失败 {}", point, e);
            return PointOutcome.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * 早盘强制重算一次预估，页面打开即有当日数据
     *
     * @return 产出结果
     */
    private PointOutcome morning() {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setRefresh(true);
        return fromEstimate(estimateService.estimateBatch(request), "早盘预估已刷新");
    }

    /**
     * 午盘复用当日缓存刷新页面数据，缓存过期才重算
     *
     * @return 产出结果
     */
    private PointOutcome noon() {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setRefresh(false);
        return fromEstimate(estimateService.estimateBatch(request), "午盘预估已刷新");
    }

    /**
     * 收盘前组装并发送提醒邮件，正文留存路径由邮件实现返回
     *
     * @param tradeDate 交易日
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome preClose(LocalDate tradeDate, boolean resend) {
        CloseReminderPort port = closeReminderPorts.getIfAvailable();
        if (port == null) {
            return PointOutcome.fail("收盘前邮件通道未接入");
        }
        String contentPath = port.send(tradeDate, resend);
        PointOutcome outcome = PointOutcome.ok(resend ? "收盘前提醒已发送（含补发）" : "收盘前提醒已发送");
        outcome.setContentPath(contentPath);
        return outcome;
    }

    /**
     * 晚间跑一次净值校准，产出当日误差
     *
     * @return 产出结果
     */
    private PointOutcome evening() {
        CalibrationRequest request = new CalibrationRequest();
        CalibrationResultVO result = estimateCalibrationService.calibrate(request);
        if (result == null) {
            return PointOutcome.fail("校准未返回结果");
        }
        return PointOutcome.ok("晚间校准完成：对照 " + result.getCalibratedCount() + " 只，超阈值 "
                + result.getOverThresholdCount() + " 只，缺口 " + result.getGapCount() + " 只");
    }

    /**
     * 把预估总览折成一句可读摘要，全部无法预估时按失败处理
     *
     * @param result 预估总览
     * @param prefix 摘要前缀
     * @return 产出结果
     */
    private PointOutcome fromEstimate(EstimateBatchVO result, String prefix) {
        if (result == null) {
            return PointOutcome.fail("预估未返回结果");
        }
        if (result.getRequested() != null && result.getRequested() > 0
                && (result.getEstimableCount() == null || result.getEstimableCount() == 0)) {
            return PointOutcome.fail("全部持仓今日无法预估");
        }
        return PointOutcome.ok(prefix + "：可预估 " + result.getEstimableCount() + " 只，无法预估 "
                + result.getUnavailableCount() + " 只");
    }
}
