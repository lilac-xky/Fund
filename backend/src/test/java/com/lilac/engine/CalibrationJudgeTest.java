package com.lilac.engine;

import com.lilac.engine.model.EstimateCalibrationVerdict;
import com.lilac.enums.ChangeDirection;
import com.lilac.enums.FundCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 晚间校准判定测试，核心是分类型阈值、混合型只判方向与区间
 */
class CalibrationJudgeTest {

    private CalibrationJudge judge;
    private EstimateThresholds thresholds;

    /**
     * 装配判定器与默认阈值
     */
    @BeforeEach
    void setUp() {
        judge = new CalibrationJudge();
        thresholds = new EstimateThresholds();
    }

    /**
     * 股票型误差超过 0.3 百分点即标记超阈值
     */
    @Test
    @DisplayName("股票型超过 0.3 百分点标记超阈值")
    void stockOverStockThreshold() {
        EstimateCalibrationVerdict over = point(FundCategory.STOCK, "1.00", "0.60");
        EstimateCalibrationVerdict under = point(FundCategory.STOCK, "1.00", "0.80");

        assertThat(over.getThresholdValue()).isEqualByComparingTo("0.3");
        assertThat(over.getOverThreshold()).isTrue();
        assertThat(over.getErrorValue()).isEqualByComparingTo("0.400000");
        assertThat(under.getOverThreshold()).isFalse();
    }

    /**
     * 债券型用 0.10 的独立阈值，同一个误差在股票型下合格、在债券型下超阈值
     */
    @Test
    @DisplayName("债券型使用独立阈值 0.10")
    void bondUsesItsOwnThreshold() {
        EstimateCalibrationVerdict bond = point(FundCategory.BOND, "0.20", "0.05");
        EstimateCalibrationVerdict stock = point(FundCategory.STOCK, "0.20", "0.05");

        assertThat(bond.getThresholdValue()).isEqualByComparingTo("0.10");
        assertThat(bond.getOverThreshold()).isTrue();
        assertThat(stock.getThresholdValue()).isEqualByComparingTo("0.3");
        assertThat(stock.getOverThreshold()).isFalse();
    }

    /**
     * 股票型、红利、黄金都走 0.3 阈值，不与债券混用
     */
    @Test
    @DisplayName("股票型红利黄金共用 0.3 阈值")
    void nonBondShareStockThreshold() {
        for (FundCategory category : new FundCategory[]{FundCategory.STOCK, FundCategory.DIVIDEND,
                FundCategory.GOLD, FundCategory.ETF}) {
            assertThat(point(category, "1.00", "0.50").getThresholdValue())
                    .as("%s 的阈值", category).isEqualByComparingTo("0.3");
        }
    }

    /**
     * 混合型不填阈值也不判点估计误差，只比对方向与区间
     */
    @Test
    @DisplayName("混合型不参与点估计阈值判定")
    void mixedIgnoresPointError() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.MIXED, ChangeDirection.UP, null,
                new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("3.00"), thresholds);

        assertThat(verdict.getThresholdValue()).isNull();
        assertThat(verdict.getOverThreshold()).isFalse();
        assertThat(verdict.getDirectionHit()).isTrue();
        assertThat(verdict.getInInterval()).isTrue();
        assertThat(verdict.getIntervalWidth()).isEqualByComparingTo("2.000000");
    }

    /**
     * 混合型方向反了即判超阈值，即使实际值仍落在区间内
     */
    @Test
    @DisplayName("混合型方向不命中即超阈值")
    void mixedDirectionMissIsOver() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.MIXED, ChangeDirection.UP, null,
                new BigDecimal("-1.00"), new BigDecimal("-2.00"), new BigDecimal("2.00"), thresholds);

        assertThat(verdict.getDirectionHit()).isFalse();
        assertThat(verdict.getInInterval()).isTrue();
        assertThat(verdict.getOverThreshold()).isTrue();
    }

    /**
     * 混合型实际值跑出区间即判超阈值，即使方向对了
     */
    @Test
    @DisplayName("混合型落在区间外即超阈值")
    void mixedOutOfIntervalIsOver() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.MIXED, ChangeDirection.DOWN, null,
                new BigDecimal("-3.50"), new BigDecimal("-1.00"), new BigDecimal("0.50"), thresholds);

        assertThat(verdict.getDirectionHit()).isTrue();
        assertThat(verdict.getInInterval()).isFalse();
        assertThat(verdict.getOverThreshold()).isTrue();
    }

    /**
     * 方向与实际都靠近零时按横盘命中，避免白噪声算成未命中
     */
    @Test
    @DisplayName("两侧都横盘时按命中处理")
    void bothFlatCountsAsHit() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.MIXED, ChangeDirection.FLAT, null,
                new BigDecimal("0.01"), new BigDecimal("-0.50"), new BigDecimal("0.50"), thresholds);

        assertThat(verdict.getDirectionHit()).isTrue();
        assertThat(verdict.getOverThreshold()).isFalse();
    }

    /**
     * 混合型没有区间可对照时退化为只看方向
     */
    @Test
    @DisplayName("无区间时只判方向")
    void mixedWithoutInterval() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.MIXED, ChangeDirection.DOWN, null,
                new BigDecimal("-1.20"), null, null, thresholds);

        assertThat(verdict.getInInterval()).isNull();
        assertThat(verdict.getIntervalWidth()).isNull();
        assertThat(verdict.getOverThreshold()).isFalse();

        EstimateCalibrationVerdict missed = judge.judge(FundCategory.MIXED, ChangeDirection.DOWN, null,
                new BigDecimal("1.20"), null, null, thresholds);
        assertThat(missed.getOverThreshold()).isTrue();
    }

    /**
     * 涨跌幅转方向，绝对值小于 0.05 百分点按横盘
     */
    @Test
    @DisplayName("涨跌幅转方向按 0.05 判横盘")
    void directionOfChange() {
        BigDecimal floor = thresholds.getDirectionFloor();

        assertThat(judge.directionOf(new BigDecimal("0.04"), floor)).isEqualTo(ChangeDirection.FLAT);
        assertThat(judge.directionOf(new BigDecimal("-0.04"), floor)).isEqualTo(ChangeDirection.FLAT);
        assertThat(judge.directionOf(new BigDecimal("0.06"), floor)).isEqualTo(ChangeDirection.UP);
        assertThat(judge.directionOf(new BigDecimal("-0.06"), floor)).isEqualTo(ChangeDirection.DOWN);
    }

    /**
     * 非混合型当时没给数字时视为超阈值，避免把「不可预估」当成合格
     */
    @Test
    @DisplayName("非混合型无点估计时按超阈值处理")
    void missingPointIsOver() {
        EstimateCalibrationVerdict verdict = judge.judge(FundCategory.STOCK, null, null,
                new BigDecimal("1.00"), null, null, thresholds);

        assertThat(verdict.getOverThreshold()).isTrue();
        assertThat(verdict.getErrorValue()).isNull();
    }

    /**
     * 构造一条点估计校准，预估与实际都给
     *
     * @param category  基金类型
     * @param estimated 预估涨跌幅
     * @param actual    实际涨跌幅
     * @return 判定结论
     */
    private EstimateCalibrationVerdict point(FundCategory category, String estimated, String actual) {
        return judge.judge(category, null, new BigDecimal(estimated), new BigDecimal(actual),
                null, null, thresholds);
    }
}
