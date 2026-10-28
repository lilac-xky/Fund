package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.enums.SendPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 时点计划表，把配置的 cron 解成「时点 + 应发时刻 + 可直接注册的表达式」，并判断交易日与补跑时效
 */
@Slf4j
@Component
public class TradingCalendar {

    private final TaskProperties taskProperties;

    /**
     * 注入调度配置，计划表每次现算，改配置后重启即生效
     *
     * @param taskProperties 调度配置
     */
    public TradingCalendar(TaskProperties taskProperties) {
        this.taskProperties = taskProperties;
    }

    /**
     * 取按时刻排序后的启用时点计划，供注册定时器、补跑与总览逐条比对
     *
     * @return 时点计划列表
     */
    public List<PointSchedule> schedules() {
        List<PointSchedule> schedules = new ArrayList<>();
        for (Map.Entry<String, TaskProperties.PointProperties> entry : taskProperties.getPoints().entrySet()) {
            TaskProperties.PointProperties config = entry.getValue();
            if (config == null || !config.isEnabled()) {
                continue;
            }
            SendPoint point = parsePoint(entry.getKey());
            if (point == null) {
                continue;
            }
            String cron = normalizeCron(entry.getKey(), config.getCron());
            LocalTime time = resolveTime(entry.getKey(), config, cron);
            if (cron == null || time == null) {
                continue;
            }
            schedules.add(new PointSchedule(point, time, cron));
        }
        schedules.sort((left, right) -> left.getTime().compareTo(right.getTime()));
        return schedules;
    }

    /**
     * 取单个时点的计划，未配置或未启用时返回 null
     *
     * @param point 时点
     * @return 时点计划，无则 null
     */
    public PointSchedule scheduleOf(SendPoint point) {
        return schedules().stream()
                .filter(item -> item.getPoint() == point)
                .findFirst()
                .orElse(null);
    }

    /**
     * 巡检任务的 cron，写错时退回每小时第 5 分钟，保证兜底通道始终存在
     *
     * @return 六段 cron 表达式
     */
    public String sweepCron() {
        return normalizeCron("SWEEP", taskProperties.getSweepCron()) == null
                ? "0 5 * * * *" : normalizeCron("SWEEP", taskProperties.getSweepCron());
    }

    /**
     * 判断是否为交易日，周末与自行维护的节假日清单都不算
     *
     * @param date 日期
     * @return 交易日返回 true
     */
    public boolean isTradingDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> !isHoliday(date);
        };
    }

    /**
     * 判断该日期是否在额外节假日清单中
     *
     * @param date 日期
     * @return 节假日返回 true
     */
    public boolean isHoliday(LocalDate date) {
        if (date == null || taskProperties.getHolidays() == null) {
            return false;
        }
        String target = date.toString();
        return taskProperties.getHolidays().stream()
                .anyMatch(item -> item != null && target.equals(item.trim()));
    }

    /**
     * 判断时点是否仍在补跑时效内，超过窗口的漏跑不再补发，避免收盘后补一封过期提醒
     *
     * @param point 时点计划
     * @param date  应发日期
     * @param now   当前时间
     * @return 未过时效返回 true
     */
    public boolean withinCatchUpWindow(PointSchedule point, LocalDate date, LocalDateTime now) {
        LocalDateTime due = LocalDateTime.of(date, point.getTime());
        if (now.isBefore(due)) {
            return false;
        }
        return !now.isAfter(due.plusMinutes(Math.max(0, taskProperties.getCatchUpWindowMinutes())));
    }

    /**
     * 解析时点名，写错时只记日志跳过该条，不影响其余时点
     *
     * @param name 配置里的时点名
     * @return 时点，非法返回 null
     */
    private SendPoint parsePoint(String name) {
        try {
            return SendPoint.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            log.warn("调度配置的时点名非法，已忽略: {}", name);
            return null;
        }
    }

    /**
     * 把配置的 cron 统一成 Spring 认的六段表达式，五段标准写法自动补上秒位
     *
     * @param name 时点名，仅用于日志
     * @param cron 原始表达式
     * @return 六段表达式，非法返回 null
     */
    public String normalizeCron(String name, String cron) {
        if (cron == null || cron.isBlank()) {
            log.warn("调度配置缺少 cron，已忽略时点 {}", name);
            return null;
        }
        String trimmed = cron.trim();
        String[] parts = trimmed.split("\\s+");
        String normalized = parts.length == 5 ? "0 " + trimmed : trimmed;
        if (normalized.split("\\s+").length != 6) {
            log.warn("调度配置的 cron 字段数应为 5 段或 6 段，已忽略时点 {}：{}", name, cron);
            return null;
        }
        try {
            CronExpression.parse(normalized);
        } catch (RuntimeException e) {
            log.warn("调度配置的 cron 非法，已忽略时点 {}：{}", name, cron);
            return null;
        }
        return normalized;
    }

    /**
     * 推出时点的应发时刻，cron 能解出唯一时刻就用 cron，否则退回配置里的 time
     *
     * @param name   时点名
     * @param config 时点配置
     * @param cron   已规范化的 cron
     * @return 应发时刻，非法返回 null
     */
    private LocalTime resolveTime(String name, TaskProperties.PointProperties config, String cron) {
        LocalTime fromCron = fromCron(cron);
        if (fromCron != null) {
            return fromCron;
        }
        if (config.getTime() == null || config.getTime().isBlank()) {
            log.warn("调度配置的 cron 无法确定时刻且未给 time，已忽略时点 {}：{}", name, config.getCron());
            return null;
        }
        try {
            return LocalTime.parse(config.getTime().trim());
        } catch (DateTimeParseException e) {
            log.warn("调度配置的 time 非法，已忽略时点 {}：{}", name, config.getTime());
            return null;
        }
    }

    /**
     * 从六段 cron 的分与时字段取应发时刻，含 * 或 / 时无法确定唯一时刻
     *
     * @param cron 六段 cron
     * @return 应发时刻，无法确定返回 null
     */
    private LocalTime fromCron(String cron) {
        if (cron == null) {
            return null;
        }
        String[] parts = cron.split("\\s+");
        if (parts.length != 6) {
            return null;
        }
        try {
            int minute = Integer.parseInt(parts[1]);
            int hour = Integer.parseInt(parts[2]);
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    /**
     * 时点、应发时刻与可直接注册的 cron
     */
    public static class PointSchedule {

        private final SendPoint point;
        private final LocalTime time;
        private final String cron;

        /**
         * 记录时点、应发时刻与 cron
         *
         * @param point 时点
         * @param time  应发时刻
         * @param cron  六段 cron
         */
        public PointSchedule(SendPoint point, LocalTime time, String cron) {
            this.point = point;
            this.time = time;
            this.cron = cron;
        }

        /**
         * 取时点
         *
         * @return 时点
         */
        public SendPoint getPoint() {
            return point;
        }

        /**
         * 取应发时刻
         *
         * @return 应发时刻
         */
        public LocalTime getTime() {
            return time;
        }

        /**
         * 取六段 cron
         *
         * @return cron 表达式
         */
        public String getCron() {
            return cron;
        }
    }
}
