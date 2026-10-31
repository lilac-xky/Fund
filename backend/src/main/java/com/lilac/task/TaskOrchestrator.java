package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.domain.vo.TaskOverviewVO;
import com.lilac.domain.vo.TaskPointStatusVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import com.lilac.enums.TaskTrigger;
import com.lilac.task.model.PointOutcome;
import com.lilac.utils.ThrowUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 四时点任务编排，负责到点触发、启动补跑、时钟跳变拦截、留痕与手动触发
 */
@Slf4j
@Component
public class TaskOrchestrator {

    /**
     * 总览最多带出多少条邮件留存，过多会把页面撑爆
     */
    private static final int MAIL_ARCHIVE_LIMIT = 30;

    private final TaskProperties taskProperties;

    private final TradingCalendar tradingCalendar;

    private final ClockGuard clockGuard;

    private final SendLedger sendLedger;

    private final TaskOutcomeDispatcher dispatcher;

    private final com.lilac.mail.MailArchive mailArchive;

    /**
     * 注入调度依赖，编排层只做时序与留痕，产出交给分派器
     *
     * @param taskProperties  调度配置
     * @param tradingCalendar 交易日历
     * @param clockGuard      时钟跳变检测
     * @param sendLedger      发送留痕
     * @param dispatcher      产出分派器
     * @param mailArchive     邮件留存，供总览带出未送达件
     */
    public TaskOrchestrator(TaskProperties taskProperties, TradingCalendar tradingCalendar,
                            ClockGuard clockGuard, SendLedger sendLedger, TaskOutcomeDispatcher dispatcher,
                            com.lilac.mail.MailArchive mailArchive) {
        this.taskProperties = taskProperties;
        this.tradingCalendar = tradingCalendar;
        this.clockGuard = clockGuard;
        this.sendLedger = sendLedger;
        this.dispatcher = dispatcher;
        this.mailArchive = mailArchive;
    }

    /**
     * 启动时打印实际生效的时点表，配置写错能第一时间在日志里看见
     */
    @PostConstruct
    public void logSchedules() {
        tradingCalendar.schedules().forEach(item ->
                log.info("调度时点 {} 应发 {} 补跑窗口 {} 分钟", item.getPoint(), item.getTime(),
                        taskProperties.getCatchUpWindowMinutes()));
    }

    /**
     * 应用就绪后执行一次启动补跑，扫描当日应发未发的时点
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!taskProperties.isEnabled()) {
            log.info("定时任务已关闭，跳过启动补跑");
            return;
        }
        LocalDateTime now = now();
        int count = runCatchUp(now.toLocalDate(), now);
        log.info("启动补跑完成，补发 {} 个时点", count);
    }

    /**
     * 每小时巡检，补齐定时器未触发的时点，重启跨过时点也能在下一个巡检补上
     */
    public void sweep() {
        if (!timerActive()) {
            return;
        }
        LocalDateTime now = now();
        runCatchUp(now.toLocalDate(), now);
    }

    /**
     * 到点触发单个时点，先过交易日与时钟两道闸再产出
     *
     * @param point 时点
     */
    public void fire(SendPoint point) {
        fireAt(point, now());
    }

    /**
     * 按给定时刻触发单个时点，抽出来供用例在固定时间点验证触发路径
     *
     * @param point 时点
     * @param now   触发时刻
     */
    public void fireAt(SendPoint point, LocalDateTime now) {
        if (!timerActive()) {
            log.debug("定时器已关闭，跳过一次触发 {}", point);
            return;
        }
        String clockIssue = clockIssue(now);
        if (clockIssue != null) {
            // 跳变时宁可不发，也要在留痕里留下「为什么没发」，否则页面上只看到凭空少了一封
            sendLedger.markFailed(now.toLocalDate(), point, clockIssue, null);
            log.warn("时点 {} 因时钟异常跳过：{}", point, clockIssue);
            return;
        }
        if (!tradingCalendar.isTradingDay(now.toLocalDate())) {
            log.info("非交易日，跳过一次触发 {}", point);
            return;
        }
        if (sendLedger.alreadySent(now.toLocalDate(), point)) {
            log.info("时点 {} 今日已成功，不重复产出", point);
            return;
        }
        if (tradingCalendar.scheduleOf(point) == null) {
            log.info("时点 {} 未配置或已关闭，跳过", point);
            return;
        }
        execute(point, now.toLocalDate(), TaskTrigger.SCHEDULED, false);
    }

    /**
     * 启动补跑与巡检共用：扫描当日应发未发的时点，未过时效的立即补发
     *
     * @param date 扫描日期
     * @param now  当前时间
     * @return 本次补跑的时点数
     */
    public int runCatchUp(LocalDate date, LocalDateTime now) {
        if (!tradingCalendar.isTradingDay(date)) {
            log.info("{} 非交易日，不做补跑", date);
            return 0;
        }
        String clockIssue = clockIssue(now);
        if (clockIssue != null) {
            log.warn("时钟异常，本次补跑整体跳过：{}", clockIssue);
            return 0;
        }
        int count = 0;
        for (TradingCalendar.PointSchedule schedule : tradingCalendar.schedules()) {
            SendPoint point = schedule.getPoint();
            if (sendLedger.alreadySent(date, point)) {
                continue;
            }
            if (!tradingCalendar.withinCatchUpWindow(schedule, date, now)) {
                continue;
            }
            log.info("补跑时点 {}，应发 {}", point, schedule.getTime());
            execute(point, date, TaskTrigger.CATCH_UP, sendLedger.alreadyFailed(date, point));
            count++;
        }
        return count;
    }

    /**
     * 立即扫描当日应发未发的时点并补跑，供页面按钮与重启验证使用
     *
     * @return 本次补跑的时点数
     */
    public int catchUpNow() {
        LocalDateTime now = now();
        return runCatchUp(now.toLocalDate(), now);
    }

    /**
     * 手动触发指定时点，供使用者补发与调试使用，不受定时器开关与补跑时效限制
     *
     * @param point     时点
     * @param tradeDate 交易日，为空取今日
     * @param resend    是否附带未送达内容的补发
     * @return 本次执行结果
     */
    public PointOutcome triggerManually(SendPoint point, LocalDate tradeDate, boolean resend) {
        ThrowUtils.throwIf(point == null, HttpsCodeEnum.PARAMS_ERROR, "时点不能为空");
        LocalDate date = tradeDate == null ? now().toLocalDate() : tradeDate;
        ThrowUtils.throwIf(!tradingCalendar.isTradingDay(date), HttpsCodeEnum.PARAMS_ERROR,
                date + " 非交易日，不能手动触发");
        boolean withResend = resend || sendLedger.alreadyFailed(date, point);
        return execute(point, date, TaskTrigger.MANUAL, withResend);
    }

    /**
     * 执行一个时点：先记待发、再产出、最后按结果落已发或失败
     *
     * @param point   时点
     * @param date    交易日
     * @param trigger 触发来源
     * @param resend  是否补发
     * @return 产出结果
     */
    private PointOutcome execute(SendPoint point, LocalDate date, TaskTrigger trigger, boolean resend) {
        sendLedger.markPending(date, point);
        PointOutcome outcome = dispatcher.dispatch(point, date, resend);
        if (outcome.isSuccess()) {
            sendLedger.markSent(date, point, trigger, outcome.getContentPath());
            log.info("时点 {} 执行成功（{}）：{}", point, trigger.getLabel(), outcome.getDetail());
        } else {
            sendLedger.markFailed(date, point, outcome.getFailReason(), outcome.getContentPath());
            log.warn("时点 {} 执行失败（{}）：{}", point, trigger.getLabel(), outcome.getFailReason());
        }
        return outcome;
    }

    /**
     * 当日调度总览，逐时点给出状态与是否过期
     *
     * @param date 统计日期，为空取今日
     * @return 调度总览
     */
    public TaskOverviewVO overview(LocalDate date) {
        LocalDateTime now = now();
        LocalDate target = date == null ? now.toLocalDate() : date;
        List<TaskPointStatusVO> items = new ArrayList<>();
        int sent = 0;
        int failed = 0;
        int overdue = 0;
        for (TradingCalendar.PointSchedule schedule : tradingCalendar.schedules()) {
            TaskPointStatusVO item = statusOf(schedule, target, now);
            items.add(item);
            if (item.getStatus() == SendStatus.SENT || item.getStatus() == SendStatus.RESENT) {
                sent++;
            }
            if (item.getStatus() == SendStatus.FAILED) {
                failed++;
            }
            if (Boolean.TRUE.equals(item.getOverdue())) {
                overdue++;
            }
        }
        TaskOverviewVO vo = new TaskOverviewVO();
        vo.setTradeDate(target);
        vo.setTradingDay(tradingCalendar.isTradingDay(target));
        vo.setTradingDayNote(tradingNote(target));
        vo.setItems(items);
        vo.setSentCount(sent);
        vo.setFailedCount(failed);
        vo.setOverdueCount(overdue);
        vo.setClockJump(clockGuard.inGuardWindow(now));
        vo.setClockJumpSeconds(Math.abs(clockGuard.lastJumpSeconds()));
        vo.setNotice(notice(target, sent, failed, overdue));
        // 邮件留存一并带出，页面上才能回答「发送失败的内容在哪、还差几封没补」
        vo.setMailArchiveDir(mailArchive.mailDirText());
        vo.setMailPendingResendCount(mailArchive.pendingResendCount());
        vo.setMailArchives(mailArchive.listRecent(MAIL_ARCHIVE_LIMIT));
        return vo;
    }

    /**
     * 组装单时点状态，过应发时刻仍未成功即标为过期
     *
     * @param schedule 时点计划
     * @param date     统计日期
     * @param now      当前时间
     * @return 时点状态
     */
    private TaskPointStatusVO statusOf(TradingCalendar.PointSchedule schedule, LocalDate date, LocalDateTime now) {
        SendPoint point = schedule.getPoint();
        SendRecordVO record = sendLedger.find(date, point).orElse(null);
        TaskPointStatusVO item = new TaskPointStatusVO();
        item.setPoint(point);
        item.setPointLabel(pointLabel(point));
        item.setDueTime(schedule.getTime());
        item.setStatus(record == null ? null : record.getStatus());
        item.setStatusLabel(record == null ? "未发送" : record.getStatus().getLabel());
        item.setSentTime(record == null ? null : record.getSentTime());
        item.setFailReason(record == null ? null : record.getFailReason());
        item.setContentPath(record == null ? null : record.getContentPath());
        boolean done = record != null
                && (record.getStatus() == SendStatus.SENT || record.getStatus() == SendStatus.RESENT);
        boolean passed = now.isAfter(LocalDateTime.of(date, schedule.getTime()));
        item.setOverdue(!done && passed);
        item.setSummary(pointLabel(point) + " " + schedule.getTime() + "：" + item.getStatusLabel()
                + (item.getFailReason() == null ? "" : "（" + item.getFailReason() + "）"));
        return item;
    }

    /**
     * 给出时钟异常说明，正常时返回 null；保护窗口内优先报最近一次跳变
     *
     * @param now 当前时间
     * @return 异常说明，正常为 null
     */
    private String clockIssue(LocalDateTime now) {
        if (clockGuard.inGuardWindow(now)) {
            return "系统时钟跳变保护中，最近偏差 " + Math.abs(clockGuard.lastJumpSeconds()) + " 秒，本次跳过";
        }
        return clockGuard.sample(now)
                .map(jump -> "系统时钟" + (jump.isForward() ? "前跳 " : "回拨 ") + jump.getSeconds() + " 秒，本次跳过")
                .orElse(null);
    }

    /**
     * 定时器是否启用，总开关或定时器开关任一关闭都不触发
     *
     * @return 启用返回 true
     */
    private boolean timerActive() {
        return taskProperties.isEnabled() && taskProperties.isSchedulerEnabled();
    }

    /**
     * 取交易日说明，非交易日给出具体原因
     *
     * @param date 日期
     * @return 说明文字
     */
    private String tradingNote(LocalDate date) {
        if (tradingCalendar.isTradingDay(date)) {
            return null;
        }
        return tradingCalendar.isHoliday(date) ? "节假日休市，定时任务不触发" : "周末休市，定时任务不触发";
    }

    /**
     * 拼总览提示，把已发、失败与过期三类说清楚
     *
     * @param date    统计日期
     * @param sent    已成功数
     * @param failed  失败数
     * @param overdue 过期数
     * @return 提示文字
     */
    private String notice(LocalDate date, int sent, int failed, int overdue) {
        if (!tradingCalendar.isTradingDay(date)) {
            return date + " 非交易日，四个时点均不触发";
        }
        StringBuilder builder = new StringBuilder("今日四个时点已成功 ").append(sent).append(" 个");
        if (failed > 0) {
            builder.append("，失败 ").append(failed).append(" 个");
        }
        if (overdue > 0) {
            builder.append("，已过应发时刻仍未成功 ").append(overdue).append(" 个，可手动触发");
        }
        return builder.toString();
    }

    /**
     * 时点中文名，供页面与邮件回显
     *
     * @param point 时点
     * @return 中文名
     */
    private String pointLabel(SendPoint point) {
        return switch (point) {
            case MORNING -> "早盘";
            case NOON -> "午盘";
            case PRE_CLOSE -> "收盘前";
            case EVENING -> "晚间";
        };
    }

    /**
     * 取当前墙钟时间，时区随配置，服务器时区与使用者所在地不一致也不影响
     *
     * @return 当前时间
     */
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(taskProperties.getZone()));
    }
}
