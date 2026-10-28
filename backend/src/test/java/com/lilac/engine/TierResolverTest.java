package com.lilac.engine;

import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.engine.model.EstimateHistoryStats;
import com.lilac.enums.EstimateTier;
import com.lilac.enums.FundCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档位判定测试，核心是「按历史误差定档」与「不随单日误差跳变」
 */
class TierResolverTest {

    private TierResolver resolver;
    private ErrorHistoryCalculator calculator;
    private EstimateThresholds thresholds;

    /**
     * 装配判定器与统计器
     */
    @BeforeEach
    void setUp() {
        resolver = new TierResolver();
        calculator = new ErrorHistoryCalculator();
        thresholds = new EstimateThresholds();
    }

    /**
     * 误差足够小时给 A 档
     */
    @Test
    @DisplayName("MAE 达标给 A 档")
    void tierAWhenMaeSmall() {
        List<EstimateErrorVO> history = series(40, 0.10, 1.20);

        EstimateTier tier = resolve(history, FundCategory.STOCK, null);

        assertThat(tier).isEqualTo(EstimateTier.A);
    }

    /**
     * MAE 超 0.3 但方向准、区间窄时给 B 档，只给方向与区间
     */
    @Test
    @DisplayName("方向准且区间窄给 B 档")
    void tierBWhenDirectionGood() {
        List<EstimateErrorVO> history = mixedSeries(40);

        EstimateTier tier = resolve(history, FundCategory.MIXED, null);

        assertThat(tier).isEqualTo(EstimateTier.B);
    }

    /**
     * 误差大且方向也不准时给 C 档，页面连方向都不显示
     */
    @Test
    @DisplayName("误差大且方向不准给 C 档")
    void tierCWhenBothBad() {
        List<EstimateErrorVO> history = badDirectionSeries(40);

        EstimateTier tier = resolve(history, FundCategory.MIXED, null);

        assertThat(tier).isEqualTo(EstimateTier.C);
    }

    /**
     * 无历史时混合型默认 B 档、其余默认 A 档，不凭空判 C
     */
    @Test
    @DisplayName("无历史时按类型给默认档")
    void defaultTierByCategory() {
        assertThat(resolve(List.of(), FundCategory.MIXED, null)).isEqualTo(EstimateTier.B);
        assertThat(resolve(List.of(), FundCategory.STOCK, null)).isEqualTo(EstimateTier.A);
        assertThat(resolve(List.of(), FundCategory.GOLD, null)).isEqualTo(EstimateTier.A);
    }

    /**
     * 历史样本不足时同样走默认档，不按少量样本定档
     */
    @Test
    @DisplayName("样本不足走默认档")
    void defaultTierWhenSamplesFew() {
        List<EstimateErrorVO> history = series(5, 0.90, 1.20);

        assertThat(resolve(history, FundCategory.STOCK, null)).isEqualTo(EstimateTier.A);
    }

    /**
     * 长窗口判 A、短窗口判 B 时保持原档位，档位不随单日误差跳变
     */
    @Test
    @DisplayName("两窗口不一致时沿用原档位")
    void keepsPreviousTierWhenWindowsDisagree() {
        // 前 40 条误差很小（长窗口偏 A），最后 20 条误差突然变大
        List<EstimateErrorVO> history = new ArrayList<>();
        history.addAll(series(40, 0.05, 1.20));
        history.addAll(series(20, 0.60, 1.20));

        EstimateTier kept = resolve(history, FundCategory.STOCK, EstimateTier.A);

        assertThat(kept).isEqualTo(EstimateTier.A);
    }

    /**
     * 造一段混合型历史：误差 ±0.45、方向全部命中，实际涨跌 ±2.40 使区间宽比小于 1
     *
     * @param count 条数
     * @return 历史序列
     */
    private List<EstimateErrorVO> mixedSeries(int count) {
        List<EstimateErrorVO> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double actual = i % 2 == 0 ? 2.40 : -2.40;
            double estimated = actual + (i % 2 == 0 ? 0.45 : -0.45);
            history.add(row(estimated, actual));
        }
        return history;
    }

    /**
     * 按给定误差与方向构造历史序列
     *
     * @param count         条数
     * @param mae           每条的平均绝对误差
     * @param actual        每条的实际涨跌幅
     * @return 历史序列
     */
    private List<EstimateErrorVO> series(int count, double mae, double actual) {
        List<EstimateErrorVO> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 正负交替但同号，方向命中率为 100%
            double estimated = actual + (i % 2 == 0 ? mae : -mae);
            history.add(row(estimated, actual));
        }
        return history;
    }

    /**
     * 方向全错的序列，用于判 C 档
     *
     * @param count 条数
     * @return 历史序列
     */
    private List<EstimateErrorVO> badDirectionSeries(int count) {
        List<EstimateErrorVO> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 预估与实际永远反号，方向准确率 0
            history.add(row(-1.20, 1.10));
        }
        return history;
    }

    /**
     * 用长窗口统计判定档位
     *
     * @param history  历史序列
     * @param category 基金类型
     * @param previous 原档位
     * @return 档位
     */
    private EstimateTier resolve(List<EstimateErrorVO> history, FundCategory category, EstimateTier previous) {
        EstimateHistoryStats stats = calculator.summarize(history, thresholds.getHistoryWindow(), thresholds.getSigmaFactor());
        EstimateHistoryStats recent = calculator.summarize(history, thresholds.getShortWindow(), thresholds.getSigmaFactor());
        return resolver.resolve(category, stats, recent, previous, thresholds);
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
