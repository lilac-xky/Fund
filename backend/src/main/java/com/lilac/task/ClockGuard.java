package com.lilac.task;

import com.lilac.config.TaskProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 时钟跳变检测，靠「墙钟推进」与「单调时钟推进」的偏差判断系统时间是否被改动
 */
@Slf4j
@Component
public class ClockGuard {

    private final TaskProperties taskProperties;

    /**
     * 上次采样的单调时钟读数，纳秒；nanoTime 不受系统时间调整影响
     */
    private long lastNanos;

    /**
     * 上次采样的墙钟时间，为空表示尚未建立基线
     */
    private LocalDateTime lastWallClock;

    /**
     * 最近一次跳变发生时的墙钟时间，供保护窗口判断
     */
    private LocalDateTime lastJumpAt;

    /**
     * 最近一次跳变的偏差秒数，带符号，正数为前跳
     */
    private long lastJumpSeconds;

    /**
     * 注入调度配置，阈值与保护时长取启动时配置
     *
     * @param taskProperties 调度配置
     */
    public ClockGuard(TaskProperties taskProperties) {
        this.taskProperties = taskProperties;
    }

    /**
     * 用系统单调时钟采样一次当前时刻
     *
     * @param now 当前墙钟时间
     * @return 判定为跳变时返回跳变详情
     */
    public Optional<ClockJump> sample(LocalDateTime now) {
        return sample(now, System.nanoTime());
    }

    /**
     * 按给定单调读数采样，首次采样只建基线，避免启动瞬间被误判成跳变
     *
     * @param now            当前墙钟时间
     * @param monotonicNanos 单调时钟读数，纳秒
     * @return 判定为跳变时返回跳变详情
     */
    public Optional<ClockJump> sample(LocalDateTime now, long monotonicNanos) {
        if (lastWallClock == null) {
            lastWallClock = now;
            lastNanos = monotonicNanos;
            return Optional.empty();
        }
        // 单调时钟只增不减，它与墙钟推进量之差就是系统时间被改动的那一部分
        long monotonicMillis = (monotonicNanos - lastNanos) / 1_000_000L;
        long wallMillis = Duration.between(lastWallClock, now).toMillis();
        long driftMillis = wallMillis - monotonicMillis;
        lastWallClock = now;
        lastNanos = monotonicNanos;
        if (Math.abs(driftMillis) <= thresholdMillis()) {
            return Optional.empty();
        }
        long driftSeconds = Math.round(Math.abs(driftMillis) / 1000.0);
        ClockJump jump = new ClockJump(driftSeconds, driftMillis > 0);
        lastJumpAt = now;
        lastJumpSeconds = driftMillis > 0 ? driftSeconds : -driftSeconds;
        log.warn("检测到系统时钟跳变 {} 秒，方向 {}，当前时间 {}", driftSeconds,
                jump.isForward() ? "前跳" : "回拨", now);
        return Optional.of(jump);
    }

    /**
     * 判断当前是否处在跳变保护窗口内，窗口内的时点一律跳过
     *
     * @param now 当前时间
     * @return 保护窗口内返回 true
     */
    public boolean inGuardWindow(LocalDateTime now) {
        if (lastJumpAt == null || now == null) {
            return false;
        }
        return !now.isAfter(lastJumpAt.plusMinutes(Math.max(0, taskProperties.getClockJumpGuardMinutes())));
    }

    /**
     * 取最近一次跳变的偏差秒数，带符号，无跳变返回 0
     *
     * @return 偏差秒数
     */
    public long lastJumpSeconds() {
        return lastJumpSeconds;
    }

    /**
     * 当前生效的跳变阈值毫秒数，配置非正时按 300 秒兜底
     *
     * @return 阈值毫秒
     */
    private long thresholdMillis() {
        int seconds = taskProperties.getClockJumpThresholdSeconds() > 0
                ? taskProperties.getClockJumpThresholdSeconds() : 300;
        return seconds * 1000L;
    }

    /**
     * 跳变结果，带偏差秒数与方向
     */
    public static class ClockJump {

        private final long seconds;
        private final boolean forward;

        /**
         * 记录跳变偏差与方向
         *
         * @param seconds 偏差秒数，取绝对值
         * @param forward 是否前跳
         */
        public ClockJump(long seconds, boolean forward) {
            this.seconds = seconds;
            this.forward = forward;
        }

        /**
         * 取偏差秒数
         *
         * @return 偏差秒数
         */
        public long getSeconds() {
            return seconds;
        }

        /**
         * 是否前跳
         *
         * @return 前跳返回 true
         */
        public boolean isForward() {
            return forward;
        }
    }
}
