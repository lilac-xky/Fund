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
 * 各时点产出编排，四个时点都先算数再发信，正文详略随时点变化
 */
@Slf4j
@Component
public class TaskOutcomeDispatcher {

    private final EstimateService estimateService;

    private final EstimateCalibrationService estimateCalibrationService;

    private final ObjectProvider<ReminderMailPort> reminderMailPorts;

    /**
     * 注入四个时点所需的产出能力，邮件出口按需解析，未接入时只报失败不阻断启动
     *
     * @param estimateService            预估业务
     * @param estimateCalibrationService 晚间校准业务
     * @param reminderMailPorts          时点提醒邮件出口，可能尚未接入
     */
    public TaskOutcomeDispatcher(EstimateService estimateService,
                                 EstimateCalibrationService estimateCalibrationService,
                                 ObjectProvider<ReminderMailPort> reminderMailPorts) {
        this.estimateService = estimateService;
        this.estimateCalibrationService = estimateCalibrationService;
        this.reminderMailPorts = reminderMailPorts;
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
                case MORNING -> morning(tradeDate, resend);
                case NOON -> noon(tradeDate, resend);
                case PRE_CLOSE -> preClose(tradeDate, resend);
                case EVENING -> evening(tradeDate, resend);
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
     * 早盘强制重算一次预估，算完立刻发信，页面与邮件用的是同一批数字
     *
     * @param tradeDate 交易日
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome morning(LocalDate tradeDate, boolean resend) {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setRefresh(true);
        return fromEstimate(SendPoint.MORNING, tradeDate, estimateOf(request), resend);
    }

    /**
     * 午盘复用当日缓存刷新页面数据，缓存过期才重算，算完发信
     *
     * @param tradeDate 交易日
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome noon(LocalDate tradeDate, boolean resend) {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setRefresh(false);
        return fromEstimate(SendPoint.NOON, tradeDate, estimateOf(request), resend);
    }

    /**
     * 收盘前算出当日预估后发完整版提醒，含策略、网格、债券、指标与教训
     *
     * @param tradeDate 交易日
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome preClose(LocalDate tradeDate, boolean resend) {
        EstimateBatchRequest request = new EstimateBatchRequest();
        request.setRefresh(false);
        return fromEstimate(SendPoint.PRE_CLOSE, tradeDate, estimateOf(request), resend);
    }

    /**
     * 取当日预估，取不到时返回 null 让正文按「无法预估」或「未配置持仓」渲染，不阻断发信
     *
     * @param request 预估请求
     * @return 预估总览，失败返回 null
     */
    private EstimateBatchVO estimateOf(EstimateBatchRequest request) {
        try {
            return estimateService.estimateBatch(request);
        } catch (RuntimeException e) {
            log.warn("取预估失败，正文按无法预估处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 晚间跑一次净值校准，把逐只误差作为正文发出，不再顺带发预估
     *
     * @param tradeDate 交易日
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome evening(LocalDate tradeDate, boolean resend) {
        CalibrationRequest request = new CalibrationRequest();
        request.setTradeDate(tradeDate);
        CalibrationResultVO result = estimateCalibrationService.calibrate(request);
        if (result == null) {
            return PointOutcome.fail("校准未返回结果");
        }
        String detail = "晚间校准完成：对照 " + result.getCalibratedCount() + " 只，超阈值 "
                + result.getOverThresholdCount() + " 只，缺口 " + result.getGapCount() + " 只";
        return send(SendPoint.EVENING, tradeDate, null, result, resend, detail);
    }

    /**
     * 把预估总览折成摘要后发信，取不到预估也照发，正文会写明原因
     *
     * @param point     时点
     * @param tradeDate 交易日
     * @param result    预估总览，可为空
     * @param resend    是否补发
     * @return 产出结果
     */
    private PointOutcome fromEstimate(SendPoint point, LocalDate tradeDate, EstimateBatchVO result, boolean resend) {
        if (result == null) {
            // 取不到预估不发信会让使用者以为系统坏了，PRD 第 9 节要求正文明确标注
            return send(point, tradeDate, null, null, resend, point.getLabel() + "预估未产出，正文标注今日无法预估");
        }
        String detail = point.getLabel() + "预估已刷新：可预估 " + result.getEstimableCount() + " 只，无法预估 "
                + result.getUnavailableCount() + " 只";
        boolean allUnavailable = result.getRequested() != null && result.getRequested() > 0
                && (result.getEstimableCount() == null || result.getEstimableCount() == 0);
        // 全部无法预估仍要发信，PRD 要求这种情况正文明确标注，而不是静默不发
        return send(point, tradeDate, result, null, resend, allUnavailable ? detail + "（今日全部无法预估）" : detail);
    }

    /**
     * 统一走邮件出口，发送成功才带出留存路径，失败由异常转成失败结果
     *
     * @param point       时点
     * @param tradeDate   交易日
     * @param estimate    预估总览，晚间为空
     * @param calibration 校准结果，其余时点为空
     * @param resend      是否补发
     * @param detail      产出摘要
     * @return 产出结果
     */
    private PointOutcome send(SendPoint point, LocalDate tradeDate, EstimateBatchVO estimate,
                              CalibrationResultVO calibration, boolean resend, String detail) {
        ReminderMailPort port = reminderMailPorts.getIfAvailable();
        if (port == null) {
            PointOutcome outcome = PointOutcome.fail("提醒邮件通道未接入");
            outcome.setDetail(detail);
            return outcome;
        }
        String contentPath = port.send(point, tradeDate, estimate, calibration, resend);
        PointOutcome outcome = PointOutcome.ok(detail + "，邮件已发送" + (resend ? "（含补发）" : ""));
        outcome.setContentPath(contentPath);
        return outcome;
    }
}
