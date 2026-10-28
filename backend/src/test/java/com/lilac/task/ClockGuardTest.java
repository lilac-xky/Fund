package com.lilac.task;

import com.lilac.config.TaskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时钟跳变检测测试，正常推进不误报，墙钟被改动才报跳变并进入保护窗口
 */
class ClockGuardTest {

    private static final long NANOS = 1_000_000_000L;

    private TaskProperties properties;
    private ClockGuard guard;

    /**
     * 阈值 300 秒、保护 10 分钟，接近线上的默认配置
     */
    @BeforeEach
    void setUp() {
        properties = new TaskProperties();
        properties.setClockJumpThresholdSeconds(300);
        properties.setClockJumpGuardMinutes(10);
        guard = new ClockGuard(properties);
    }

    /**
     * 墙钟只前进几秒属于正常，不判跳变也不进保护窗口
     */
    @Test
    @DisplayName("正常推进不判跳变")
    void normalTickIsNotJump() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 14, 25);
        assertThat(guard.sample(base, NANOS)).isEmpty();
        Optional<ClockGuard.ClockJump> jump = guard.sample(base.plusSeconds(2), NANOS + 2_000_000_000L);
        assertThat(jump).isEmpty();
        assertThat(guard.inGuardWindow(base.plusSeconds(2))).isFalse();
        assertThat(guard.lastJumpSeconds()).isZero();
    }

    /**
     * 墙钟比单调时钟多走一小时即前跳，且保护窗口从跳变时刻起算
     */
    @Test
    @DisplayName("墙钟前跳被判为跳变并开启保护窗口")
    void forwardJumpDetected() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 14, 0);
        guard.sample(base, NANOS);
        Optional<ClockGuard.ClockJump> jump = guard.sample(base.plusHours(1), NANOS + 1_000_000_000L);
        assertThat(jump).isPresent();
        assertThat(jump.get().isForward()).isTrue();
        assertThat(jump.get().getSeconds()).isBetween(3590L, 3600L);
        assertThat(guard.inGuardWindow(base.plusHours(1))).isTrue();
        assertThat(guard.inGuardWindow(base.plusHours(1).plusMinutes(9))).isTrue();
        assertThat(guard.inGuardWindow(base.plusHours(1).plusMinutes(11))).isFalse();
    }

    /**
     * 墙钟回拨同样判为跳变，偏差记为负数，免得页面把回拨显示成前跳
     */
    @Test
    @DisplayName("墙钟回拨被判为跳变且偏差为负")
    void backwardJumpDetected() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 14, 0);
        guard.sample(base, NANOS);
        Optional<ClockGuard.ClockJump> jump = guard.sample(base.minusHours(2), NANOS + 1_000_000_000L);
        assertThat(jump).isPresent();
        assertThat(jump.get().isForward()).isFalse();
        assertThat(guard.lastJumpSeconds()).isNegative();
    }

    /**
     * 首次采样只建立基线，不把启动瞬间当成跳变
     */
    @Test
    @DisplayName("首次采样不判跳变")
    void firstSampleIsBaseline() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 9, 30);
        assertThat(guard.sample(base, NANOS)).isEmpty();
        assertThat(guard.inGuardWindow(base)).isFalse();
    }

    /**
     * 阈值以内的抖动不判跳变，系统校时几十秒属正常
     */
    @Test
    @DisplayName("阈值内的校时不算跳变")
    void smallAdjustmentIgnored() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 28, 9, 30);
        guard.sample(base, NANOS);
        assertThat(guard.sample(base.plusSeconds(30), NANOS + 1_000_000_000L)).isEmpty();
        assertThat(guard.inGuardWindow(base.plusSeconds(30))).isFalse();
    }
}
