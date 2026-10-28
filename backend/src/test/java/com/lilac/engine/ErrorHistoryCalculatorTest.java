package com.lilac.engine;

import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.engine.model.EstimateHistoryStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 误差统计测试，覆盖 MAE、方向准确率、区间半宽与宽波动比
 */
class ErrorHistoryCalculatorTest {

    private ErrorHistoryCalculator calculator;

    /**
     * 装配统计器
     */
    @BeforeEach
    void setUp() {
        calculator = new ErrorHistoryCalculator();
    }

    /**
     * 无历史时返回空统计，档位判定据此走默认档
     */
    @Test
    @DisplayName("无历史返回空统计")
    void emptyWhenNoHistory() {
        EstimateHistoryStats stats = calculator.summarize(List.of(), 60, new BigDecimal("1.64"));

        assertThat(stats.getSampleCount()).isZero();
        assertThat(stats.getMae()).isNull();
        assertThat(stats.enough(20)).isFalse();
    }

    /**
     * MAE 与区间半宽按窗口内样本算出，误差恒定时 sigma 为零、半宽也为零
     */
    @Test
    @DisplayName("MAE 与区间半宽按窗口计算")
    void computesMaeAndInterval() {
        List<EstimateErrorVO> history = new ArrayList<>();
        history.add(row(1.00, 0.90));
        history.add(row(-0.50, -0.60));
        history.add(row(0.20, 0.10));

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getSampleCount()).isEqualTo(3);
        assertThat(stats.getMae()).isEqualByComparingTo("0.100000");
        assertThat(stats.getErrorSigma()).isEqualByComparingTo("0.000000");
        assertThat(stats.getIntervalHalfWidth()).isEqualByComparingTo("0.000000");
        assertThat(stats.getDirectionAccuracy()).isEqualByComparingTo("1.0000");
    }

    /**
     * 超过窗口只取最近 N 条，早期样本不参与判定
     */
    @Test
    @DisplayName("只统计窗口内的最近样本")
    void usesWindowTail() {
        List<EstimateErrorVO> history = new ArrayList<>();
        // 前 30 条误差很大，后 20 条误差很小
        for (int i = 0; i < 30; i++) {
            history.add(row(1.00, 2.00));
        }
        for (int i = 0; i < 20; i++) {
            history.add(row(1.00, 1.05));
        }

        EstimateHistoryStats all = calculator.summarize(history, 0, new BigDecimal("1.64"));
        EstimateHistoryStats recent = calculator.summarize(history, 20, new BigDecimal("1.64"));

        assertThat(all.getSampleCount()).isEqualTo(50);
        // (30 × 1.00 + 20 × 0.05) ÷ 50 = 0.62
        assertThat(all.getMae()).isEqualByComparingTo("0.620000");
        assertThat(recent.getSampleCount()).isEqualTo(20);
        assertThat(recent.getMae()).isEqualByComparingTo("0.050000");
    }

    /**
     * 方向判定只看涨跌同号，零附近的样本不参与，避免白噪声被算成命中
     */
    @Test
    @DisplayName("零附近样本不参与方向判定")
    void directionIgnoresNearZero() {
        List<EstimateErrorVO> history = new ArrayList<>();
        history.add(row(0.01, 0.80));
        history.add(row(1.20, -1.00));
        history.add(row(0.90, 1.30));

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        // 只有后两条参与方向判定，命中一条
        assertThat(stats.getDirectionAccuracy()).isEqualByComparingTo("0.5000");
    }

    /**
     * 区间宽超过自身波动时宽波动比大于 1，B 档据此降级
     */
    @Test
    @DisplayName("区间宽波动比可判定区间是否有信息量")
    void widthRatioOverOne() {
        List<EstimateErrorVO> history = new ArrayList<>();
        history.add(row(0.10, 3.00));
        history.add(row(-2.80, 2.90));
        history.add(row(2.60, -0.20));
        history.add(row(0.10, 0.30));

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getWidthRatio()).isNotNull();
        assertThat(stats.getWidthRatio()).isGreaterThan(new BigDecimal("1.0"));
    }

    /**
     * 构造一条误差记录
     *
     * @param estimated 预估涨跌幅
     * @param actual    实际涨跌幅
     * @return 误差记录
     */
    private EstimateErrorVO row(double estimated, double actual) {
        EstimateErrorVO vo = new EstimateErrorVO();
        vo.setTradeDate(LocalDate.of(2026, 9, 1));
        vo.setEstimatedChange(BigDecimal.valueOf(estimated));
        vo.setActualChange(BigDecimal.valueOf(actual));
        vo.setErrorValue(BigDecimal.valueOf(estimated - actual));
        return vo;
    }
}
