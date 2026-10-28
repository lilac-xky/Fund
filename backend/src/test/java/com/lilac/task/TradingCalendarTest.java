package com.lilac.task;

import com.lilac.config.TaskProperties;
import com.lilac.enums.SendPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易日历与补跑时效测试，重点是周末、节假日与窗口边界
 */
class TradingCalendarTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 31);

    private TaskProperties properties;
    private TradingCalendar calendar;

    /**
     * 装配四个默认时点，收盘前 14:30 作为窗口判断的样本
     */
    @BeforeEach
    void setUp() {
        properties = new TaskProperties();
        properties.setCatchUpWindowMinutes(120);
        properties.setHolidays(List.of("2026-10-01"));
        Map<String, TaskProperties.PointProperties> points = new LinkedHashMap<>();
        points.put("MORNING", pointOf("0 30 9 * * MON-FRI", "09:30"));
        points.put("NOON", pointOf("0 30 11 * * MON-FRI", "11:30"));
        points.put("PRE_CLOSE", pointOf("0 30 14 * * MON-FRI", "14:30"));
        points.put("EVENING", pointOf("0 0 20 * * MON-FRI", "20:00"));
        properties.setPoints(points);
        calendar = new TradingCalendar(properties);
    }

    /**
     * 四个时点按应发时刻升序返回，cron 写整数时直接采用 cron 的时刻
     */
    @Test
    @DisplayName("时点按应发时刻排序且时刻取自 cron")
    void schedulesSortedByTime() {
        List<TradingCalendar.PointSchedule> schedules = calendar.schedules();
        assertThat(schedules).hasSize(4);
        assertThat(schedules).extracting(TradingCalendar.PointSchedule::getPoint)
                .containsExactly(SendPoint.MORNING, SendPoint.NOON, SendPoint.PRE_CLOSE, SendPoint.EVENING);
        assertThat(schedules.get(2).getTime()).isEqualTo(LocalTime.of(14, 30));
    }

    /**
     * cron 写不出确定时刻时退回配置里的 time，两条都不可用则忽略该时点
     */
    @Test
    @DisplayName("cron 无法确定时刻时退回 time 配置")
    void fallsBackToConfiguredTime() {
        TaskProperties.PointProperties config = pointOf("0 */10 9-15 * * MON-FRI", "14:30");
        properties.getPoints().put("PRE_CLOSE", config);
        assertThat(calendar.scheduleOf(SendPoint.PRE_CLOSE).getTime()).isEqualTo(LocalTime.of(14, 30));

        properties.getPoints().put("PRE_CLOSE", pointOf("0 */10 9-15 * * MON-FRI", null));
        assertThat(calendar.scheduleOf(SendPoint.PRE_CLOSE)).isNull();
    }

    /**
     * 关闭的时点不出现在计划表里，避免补跑把它当成应发
     */
    @Test
    @DisplayName("关闭的时点不进入计划表")
    void disabledPointExcluded() {
        properties.getPoints().get("EVENING").setEnabled(false);
        assertThat(calendar.schedules()).hasSize(3);
        assertThat(calendar.scheduleOf(SendPoint.EVENING)).isNull();
    }

    /**
     * 周末与节假日都不算交易日，工作日算
     */
    @Test
    @DisplayName("周末与节假日不是交易日")
    void weekendAndHolidayAreNotTradingDays() {
        assertThat(calendar.isTradingDay(FRIDAY)).isTrue();
        assertThat(calendar.isTradingDay(SATURDAY)).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 10, 1))).isFalse();
        assertThat(calendar.isHoliday(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(calendar.isHoliday(FRIDAY)).isFalse();
    }

    /**
     * 补跑窗口是闭区间：到点即算窗口内，恰好 120 分钟仍可补，超过 1 分钟不再补
     */
    @Test
    @DisplayName("补跑窗口恰好在边界上仍可补，越界不再补")
    void catchUpWindowBoundary() {
        TradingCalendar.PointSchedule preClose = calendar.scheduleOf(SendPoint.PRE_CLOSE);
        LocalDateTime due = LocalDateTime.of(FRIDAY, LocalTime.of(14, 30));
        assertThat(calendar.withinCatchUpWindow(preClose, FRIDAY, due)).isTrue();
        assertThat(calendar.withinCatchUpWindow(preClose, FRIDAY, due.plusMinutes(120))).isTrue();
        assertThat(calendar.withinCatchUpWindow(preClose, FRIDAY, due.plusMinutes(121))).isFalse();
        assertThat(calendar.withinCatchUpWindow(preClose, FRIDAY, due.minusMinutes(1))).isFalse();
    }

    /**
     * 拼一条时点配置
     *
     * @param cron cron 表达式
     * @param time 应发时刻
     * @return 时点配置
     */
    private TaskProperties.PointProperties pointOf(String cron, String time) {
        TaskProperties.PointProperties config = new TaskProperties.PointProperties();
        config.setCron(cron);
        config.setTime(time);
        return config;
    }
}
