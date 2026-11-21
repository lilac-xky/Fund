package com.lilac.config;

import com.lilac.task.DividendRefreshTask;
import com.lilac.task.TaskOrchestrator;
import com.lilac.task.TradingCalendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 定时调度注册，四个时点、巡检与股息率刷新都按配置的 cron 动态注册，改配置重启即生效
 */
@Slf4j
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private final TaskProperties taskProperties;

    private final TradingCalendar tradingCalendar;

    private final TaskOrchestrator taskOrchestrator;

    private final DividendRefreshTask dividendRefreshTask;

    /**
     * 注入调度所需的依赖，注册时只读配置与日历
     *
     * @param taskProperties      调度配置
     * @param tradingCalendar     交易日历
     * @param taskOrchestrator    时点编排
     * @param dividendRefreshTask 股息率刷新
     */
    public SchedulingConfig(TaskProperties taskProperties, TradingCalendar tradingCalendar,
                            TaskOrchestrator taskOrchestrator, DividendRefreshTask dividendRefreshTask) {
        this.taskProperties = taskProperties;
        this.tradingCalendar = tradingCalendar;
        this.taskOrchestrator = taskOrchestrator;
        this.dividendRefreshTask = dividendRefreshTask;
    }

    /**
     * 按配置注册四个时点、一条巡检与股息率刷新，总开关关闭时不注册任何定时器
     *
     * @param registrar 定时任务注册器
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!taskProperties.isEnabled() || !taskProperties.isSchedulerEnabled()) {
            log.info("定时任务已关闭，不注册任何定时器");
            return;
        }
        for (TradingCalendar.PointSchedule schedule : tradingCalendar.schedules()) {
            registrar.addCronTask(() -> taskOrchestrator.fire(schedule.getPoint()), schedule.getCron());
            log.info("注册定时时点 {} cron={} 应发 {}", schedule.getPoint(), schedule.getCron(), schedule.getTime());
        }
        registrar.addCronTask(taskOrchestrator::sweep, tradingCalendar.sweepCron());
        log.info("注册巡检任务 cron={}", tradingCalendar.sweepCron());
        // 晚间净值常常晚于 20:00 才公布，独立一条重试，齐了就发，不等下一个整点巡检
        registrar.addCronTask(taskOrchestrator::retryEvening, tradingCalendar.eveningRetryCron());
        log.info("注册晚间净值重试 cron={}", tradingCalendar.eveningRetryCron());
        // 股息率过期就取不到数，每天开盘前自动刷一次，取不到的仍走手工覆盖
        registrar.addCronTask(dividendRefreshTask::refresh, tradingCalendar.dividendRefreshCron());
        log.info("注册股息率刷新 cron={}", tradingCalendar.dividendRefreshCron());
    }
}
