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
     * 混合型没有点估计误差时，区间半宽按区间残差给，不再拿自身波动当误差量纲
     */
    @Test
    @DisplayName("混合型区间半宽按区间残差给出")
    void mixedHalfWidthUsesResidual() {
        List<EstimateErrorVO> history = new ArrayList<>();
        // 实际每天都差 6 个百分点，但中心值只偏 0.8，残差才是区间该用的量纲
        for (int i = 0; i < 20; i++) {
            double actual = i % 2 == 0 ? 6.00 : -6.00;
            EstimateErrorVO vo = new EstimateErrorVO();
            vo.setTradeDate(LocalDate.of(2026, 9, 1));
            vo.setActualChange(BigDecimal.valueOf(actual));
            vo.setCenterChange(BigDecimal.valueOf(actual + (i % 2 == 0 ? 0.80 : -0.80)));
            history.add(vo);
        }

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getResidualSamples()).isEqualTo(20);
        // 残差 σ = 0.80，半宽 1.64 × 0.80 = 1.312；按自身波动 σ=6 会得到 9.84
        assertThat(stats.getResidualSigma()).isEqualByComparingTo("0.800000");
        assertThat(stats.getIntervalHalfWidth()).isEqualByComparingTo("1.312000");
    }

    /**
     * 连残差都没有时不给半宽，绝不退回写死的默认宽度
     */
    @Test
    @DisplayName("无残差样本时不给区间半宽")
    void noHalfWidthWithoutResidual() {
        List<EstimateErrorVO> history = new ArrayList<>();
        EstimateErrorVO vo = new EstimateErrorVO();
        vo.setTradeDate(LocalDate.of(2026, 9, 1));
        vo.setActualChange(new BigDecimal("3.00"));
        vo.setDirectionHit(Boolean.TRUE);
        history.add(vo);

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getResidualSamples()).isZero();
        assertThat(stats.getIntervalHalfWidth()).isNull();
    }

    /**
     * 连续超阈值时期区间不可展示，它是区间能不能对外给的闸门
     */
    @Test
    @DisplayName("连续超阈值时区间不可展示")
    void intervalHiddenWhenConsecutiveOver() {
        List<EstimateErrorVO> history = new ArrayList<>();
        // 前 17 条残差很小，最后 3 条突然跑远，连续计数由此产生
        for (int i = 0; i < 20; i++) {
            double actual = i % 2 == 0 ? 6.00 : -6.00;
            double resid = i >= 17 ? 9.00 : 0.20;
            EstimateErrorVO vo = new EstimateErrorVO();
            vo.setTradeDate(LocalDate.of(2026, 9, 1));
            vo.setActualChange(BigDecimal.valueOf(actual));
            vo.setCenterChange(BigDecimal.valueOf(actual + resid));
            vo.setIntervalWidth(new BigDecimal("2.62"));
            vo.setInInterval(Boolean.FALSE);
            vo.setOverThreshold(i == 19);
            history.add(vo);
        }

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getIntervalHalfWidth()).isNotNull();
        // 尾部三条残差 9.00 远超半宽，连续计数为 3
        assertThat(stats.getConsecutiveOver()).isEqualTo(3);
        assertThat(stats.intervalShowable(new BigDecimal("0.80"))).isFalse();
    }

    /**
     * 没有超阈值且覆盖得住时区间可以展示，宽度大小不参与判定
     */
    @Test
    @DisplayName("未超阈值时区间可展示，不看宽度")
    void intervalShowableWhenNoConsecutiveOver() {
        List<EstimateErrorVO> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double actual = i % 2 == 0 ? 6.00 : -6.00;
            EstimateErrorVO vo = new EstimateErrorVO();
            vo.setTradeDate(LocalDate.of(2026, 9, 1));
            vo.setActualChange(BigDecimal.valueOf(actual));
            vo.setCenterChange(BigDecimal.valueOf(actual + (i % 2 == 0 ? 0.80 : -0.80)));
            vo.setInInterval(Boolean.TRUE);
            vo.setIntervalWidth(new BigDecimal("2.62"));
            vo.setOverThreshold(Boolean.FALSE);
            history.add(vo);
        }

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        assertThat(stats.getConsecutiveOver()).isZero();
        assertThat(stats.intervalShowable(new BigDecimal("0.80"))).isTrue();
        // 宽波动比仍被算出，但不再参与区间是否展示的判定
        assertThat(stats.getWidthRatio()).isNotNull();
    }

    /**
     * 覆盖率按当前半宽自洽重算，不能被历史记录里「当时那把尺子」的 inInterval 拖低
     */
    @Test
    @DisplayName("覆盖率用当前半宽重算，不读历史旧标记")
    void coverageRecomputedWithCurrentHalfWidth() {
        List<EstimateErrorVO> history = new ArrayList<>();
        // 残差恒为 ±0.80，历史标记里 inInterval 全是 false（旧区间只有 ±0.492 宽）
        for (int i = 0; i < 20; i++) {
            double actual = i % 2 == 0 ? 6.00 : -6.00;
            EstimateErrorVO vo = new EstimateErrorVO();
            vo.setTradeDate(LocalDate.of(2026, 9, 1));
            vo.setActualChange(BigDecimal.valueOf(actual));
            vo.setCenterChange(BigDecimal.valueOf(actual + (i % 2 == 0 ? 0.80 : -0.80)));
            vo.setIntervalWidth(new BigDecimal("0.984"));
            vo.setInInterval(Boolean.FALSE);
            history.add(vo);
        }

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        // 半宽 1.312 装得住 ±0.80，真实覆盖率是 100%，而不是旧标记的 0%
        assertThat(stats.getIntervalHalfWidth()).isEqualByComparingTo("1.312000");
        assertThat(stats.getIntervalCoverage()).isEqualByComparingTo("1.0000");
        assertThat(stats.getConsecutiveOver()).isZero();
        assertThat(stats.intervalShowable(new BigDecimal("0.80"))).isTrue();
    }

    /**
     * 最近几天连续超出当前区间时，连续计数按残差自洽判定
     */
    @Test
    @DisplayName("连续超阈值按残差自洽判定")
    void consecutiveOverComputedFromResiduals() {
        List<EstimateErrorVO> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double actual = i % 2 == 0 ? 1.00 : -1.00;
            double resid = i >= 18 ? 9.00 : 0.20;
            EstimateErrorVO vo = new EstimateErrorVO();
            vo.setTradeDate(LocalDate.of(2026, 9, 1));
            vo.setActualChange(BigDecimal.valueOf(actual));
            vo.setCenterChange(BigDecimal.valueOf(actual + resid));
            // 历史标记全为「没超阈值」，真实结论要看残差
            vo.setOverThreshold(Boolean.FALSE);
            history.add(vo);
        }

        EstimateHistoryStats stats = calculator.summarize(history, 60, new BigDecimal("1.64"));

        // 最后两条残差 9.00 远超半宽，连续计数应为 2
        assertThat(stats.getConsecutiveOver()).isEqualTo(2);
        assertThat(stats.intervalShowable(new BigDecimal("0.80"))).isFalse();
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
