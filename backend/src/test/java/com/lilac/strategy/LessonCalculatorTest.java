package com.lilac.strategy;

import com.lilac.adapter.model.MarketBar;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.LessonPattern;
import com.lilac.strategy.model.LessonAlert;
import com.lilac.strategy.model.LessonPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 历史教训警示测试，核心是「三类模式各自能触发」「警示带可读教训」「正常情形不误报」
 */
class LessonCalculatorTest {

    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    private LessonCalculator calculator;
    private LessonPlan plan;

    /**
     * 装配计算器与一组基准参数：追高容忍 3%、窗口 20 日、连续亏损 5 日、亏损率 5%
     */
    @BeforeEach
    void setUp() {
        calculator = new LessonCalculator();
        plan = planOf("3", 20, 5, "5", 30);
    }

    /**
     * 买入价明显高于前 20 日最高价时判为追高
     */
    @Test
    @DisplayName("买入价高于近期高点超过容忍幅度时判为追高")
    void chaseHighTriggers() {
        // 前 20 日收盘价从 1.00 缓慢涨到 1.10，买入价 1.20 高出约 9%
        List<MarketBar> bars = risingBars(20, "1.00", "0.005");
        HoldingChangeVO buy = buyOf("1.20", BASE.plusDays(25));

        LessonAlert alert = calculator.chaseHigh(bars, buy, plan);

        assertThat(alert).isNotNull();
        assertThat(alert.getPattern()).isEqualTo(LessonPattern.CHASE_HIGH);
        assertThat(alert.getRemarks()).anyMatch(text -> text.contains("高于此"));
        assertThat(alert.getRemarks()).anyMatch(text -> text.contains("容忍幅度"));
    }

    /**
     * 买入价在近期高点附近时不判追高，这是「正常情形不误报」的反例
     */
    @Test
    @DisplayName("买入价接近近期高点时不判追高")
    void chaseHighNoFalsePositive() {
        List<MarketBar> bars = risingBars(20, "1.00", "0.005");
        // 最高收盘 1.10，买入价 1.11 仅高出 0.9%，低于 3% 容忍幅度
        HoldingChangeVO buy = buyOf("1.11", BASE.plusDays(25));

        assertThat(calculator.chaseHigh(bars, buy, plan)).isNull();
    }

    /**
     * 债券已达横盘且换入品种登记天数不足时判为盲目换仓
     */
    @Test
    @DisplayName("债券横盘后换入不熟悉品种判为盲目换仓")
    void blindSwitchTriggers() {
        HoldingChangeVO sell = sellOf("OF:006327", BASE.plusDays(10));

        LessonAlert alert = calculator.blindSwitch(sell, "OF:000922", true, 3L, plan);

        assertThat(alert).isNotNull();
        assertThat(alert.getPattern()).isEqualTo(LessonPattern.BLIND_SWITCH);
        assertThat(alert.getRemarks()).anyMatch(text -> text.contains("盲目转红利"));
    }

    /**
     * 换入品种已登记够久说明是熟悉标的，不判盲目换仓
     */
    @Test
    @DisplayName("换入熟悉品种时不判盲目换仓")
    void blindSwitchNoFalsePositive() {
        HoldingChangeVO sell = sellOf("OF:006327", BASE.plusDays(10));

        // 已登记 100 天，远超 30 天阈值
        assertThat(calculator.blindSwitch(sell, "OF:000922", true, 100L, plan)).isNull();
    }

    /**
     * 债券未达横盘时不判盲目换仓，避免把正常调仓当成错误
     */
    @Test
    @DisplayName("债券未横盘时不判盲目换仓")
    void blindSwitchNeedsSideways() {
        HoldingChangeVO sell = sellOf("OF:006327", BASE.plusDays(10));

        assertThat(calculator.blindSwitch(sell, "OF:000922", false, 3L, plan)).isNull();
    }

    /**
     * 卖出前连续多日亏损且累计跌幅够深时判为恐慌卖出
     */
    @Test
    @DisplayName("连续亏损后卖出判为恐慌卖出")
    void panicSellTriggers() {
        // 20 日收盘价从 1.00 跌到 0.90，成本价 1.00，卖出时已连亏多日且亏 10%
        List<MarketBar> bars = fallingBars(20, "1.00", "0.005");
        HoldingChangeVO sell = sellOf("ETF:510300", BASE.plusDays(25));

        LessonAlert alert = calculator.panicSell(bars, sell, new BigDecimal("1.00"), plan);

        assertThat(alert).isNotNull();
        assertThat(alert.getPattern()).isEqualTo(LessonPattern.PANIC_SELL);
        assertThat(alert.getRemarks()).anyMatch(text -> text.contains("连续"));
        assertThat(alert.getRemarks()).anyMatch(text -> text.contains("累计亏损"));
    }

    /**
     * 卖出时仍在盈利的不判恐慌卖出
     */
    @Test
    @DisplayName("盈利状态下卖出不判恐慌卖出")
    void panicSellNoFalsePositive() {
        List<MarketBar> bars = risingBars(20, "1.00", "0.005");
        HoldingChangeVO sell = sellOf("ETF:510300", BASE.plusDays(25));

        assertThat(calculator.panicSell(bars, sell, new BigDecimal("1.00"), plan)).isNull();
    }

    /**
     * 亏得不够深（未达亏损率下限）时不判恐慌卖出
     */
    @Test
    @DisplayName("亏损未达深度时不判恐慌卖出")
    void panicSellNeedsDepth() {
        // 只跌 1%，低于 5% 的亏损率下限
        List<MarketBar> bars = fallingBars(20, "1.00", "0.0005");
        HoldingChangeVO sell = sellOf("ETF:510300", BASE.plusDays(25));

        assertThat(calculator.panicSell(bars, sell, new BigDecimal("1.00"), plan)).isNull();
    }

    /**
     * 三类模式的教训文案都必须可读且不含规则编号
     */
    @Test
    @DisplayName("教训文案可读且不出现规则编号")
    void lessonTextIsReadable() {
        for (LessonPattern pattern : LessonPattern.values()) {
            String text = LessonCatalog.textOf(pattern);
            assertThat(text).as("%s 必须有教训文案", pattern).isNotBlank();
            assertThat(text).contains("你记录过");
            assertThat(text).doesNotContain("CHASE_HIGH", "PANIC_SELL", "BLIND_SWITCH");
        }
    }

    /**
     * 自然日差可算，供「登记多少天」的判定
     */
    @Test
    @DisplayName("能算出两个日期的自然日差")
    void daysBetweenComputed() {
        assertThat(calculator.daysBetween(BASE, BASE.plusDays(30))).isEqualTo(30L);
        assertThat(calculator.daysBetween(null, BASE)).isNull();
    }

    /**
     * 构造匀速上涨的日线，每日涨固定点差
     *
     * @param count 根数
     * @param start 起始价
     * @param step  每日涨跌
     * @return 日线
     */
    private List<MarketBar> risingBars(int count, String start, String step) {
        return linear(count, new BigDecimal(start), new BigDecimal(step));
    }

    /**
     * 构造匀速下跌的日线
     *
     * @param count 根数
     * @param start 起始价
     * @param step  每日跌幅（正数）
     * @return 日线
     */
    private List<MarketBar> fallingBars(int count, String start, String step) {
        return linear(count, new BigDecimal(start), new BigDecimal(step).negate());
    }

    /**
     * 按固定点差生成日线
     *
     * @param count 根数
     * @param start 起始价
     * @param step  每日变化
     * @return 日线
     */
    private List<MarketBar> linear(int count, BigDecimal start, BigDecimal step) {
        List<MarketBar> bars = new ArrayList<>(count);
        BigDecimal close = start;
        for (int i = 0; i < count; i++) {
            bars.add(new MarketBar(BASE.plusDays(i), close.subtract(new BigDecimal("0.01")), close,
                    close.add(new BigDecimal("0.02")), close.subtract(new BigDecimal("0.02")),
                    BigDecimal.valueOf(1000)));
            close = close.add(step).setScale(6, RoundingMode.HALF_UP);
        }
        return bars;
    }

    /**
     * 造一条买入变动
     *
     * @param price 买入价
     * @param date  买入日期
     * @return 变动
     */
    private HoldingChangeVO buyOf(String price, LocalDate date) {
        HoldingChangeVO change = new HoldingChangeVO();
        change.setVarietyKey("ETF:510300");
        change.setVarietyName("沪深300ETF");
        change.setChangeType(HoldingChangeType.BUY);
        change.setUnitPrice(new BigDecimal(price));
        change.setOpenDate(date);
        return change;
    }

    /**
     * 造一条卖出变动
     *
     * @param varietyKey 品种主键
     * @param date       卖出日期
     * @return 变动
     */
    private HoldingChangeVO sellOf(String varietyKey, LocalDate date) {
        HoldingChangeVO change = new HoldingChangeVO();
        change.setVarietyKey(varietyKey);
        change.setVarietyName("测试品种");
        change.setChangeType(HoldingChangeType.SELL);
        change.setOpenDate(date);
        return change;
    }

    /**
     * 构造警示参数
     *
     * @param tolerance   追高容忍幅度
     * @param window      追高窗口
     * @param lossDays    连续亏损天数
     * @param lossRate    亏损率下限
     * @param familiarDays 熟悉品种天数
     * @return 参数
     */
    private LessonPlan planOf(String tolerance, int window, int lossDays, String lossRate, int familiarDays) {
        LessonPlan built = new LessonPlan();
        built.setChaseTolerance(new BigDecimal(tolerance));
        built.setChaseWindow(window);
        built.setPanicLossDays(lossDays);
        built.setPanicLossRate(new BigDecimal(lossRate));
        built.setFamiliarDays(familiarDays);
        built.setParticipateInAlert(Boolean.TRUE);
        return built;
    }
}
