package com.lilac.strategy;

import com.lilac.adapter.model.MarketBar;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.enums.LessonPattern;
import com.lilac.strategy.model.LessonAlert;
import com.lilac.strategy.model.LessonPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史教训警示计算器，纯计算不访问数据库，按三类错误模式判定是否命中并给出教训文案
 */
@Slf4j
@Component
public class LessonCalculator {

    private static final int HUNDRED = 100;
    private static final int RESULT_SCALE = 4;
    private static final int MIN_BARS = 2;

    /**
     * 判定追高：买入价高于回看窗口内最高价超过容忍幅度即命中
     *
     * @param bars   该品种的前复权日线，按日期升序
     * @param buy    买入变动记录
     * @param plan   警示参数
     * @return 命中的警示，未命中返回 null
     */
    public LessonAlert chaseHigh(List<MarketBar> bars, HoldingChangeVO buy, LessonPlan plan) {
        if (plan == null || !plan.complete() || buy == null || buy.getUnitPrice() == null
                || buy.getOpenDate() == null) {
            return null;
        }
        List<MarketBar> before = barsBefore(bars, buy.getOpenDate());
        if (before.size() < MIN_BARS) {
            return null;
        }
        int window = Math.min(plan.getChaseWindow(), before.size());
        List<MarketBar> used = before.subList(before.size() - window, before.size());
        BigDecimal highest = null;
        LocalDate highestDate = null;
        for (MarketBar bar : used) {
            if (bar.getClose() == null) {
                continue;
            }
            if (highest == null || bar.getClose().compareTo(highest) > 0) {
                highest = bar.getClose();
                highestDate = bar.getDate();
            }
        }
        if (highest == null || highest.signum() == 0) {
            return null;
        }
        BigDecimal deviation = buy.getUnitPrice().subtract(highest).multiply(BigDecimal.valueOf(HUNDRED))
                .divide(highest, RESULT_SCALE, RoundingMode.HALF_UP);
        if (deviation.compareTo(plan.getChaseTolerance()) <= 0) {
            return null;
        }
        LessonAlert alert = base(LessonPattern.CHASE_HIGH, buy);
        alert.setTriggerValue(buy.getUnitPrice());
        alert.setReferenceValue(highest);
        alert.setTriggerDate(buy.getOpenDate());
        alert.getRemarks().add("买入价 " + plain(buy.getUnitPrice()) + " 元，高于此前 " + used.size()
                + " 个交易日最高收盘价 " + plain(highest) + " 元（" + highestDate + "）"
                + plain(deviation) + "%，超过容忍幅度 " + plain(plan.getChaseTolerance()) + "%");
        return alert;
    }

    /**
     * 判定盲目换仓：转出方向是债券且债券已达横盘阈值，同时换入品种登记天数不足
     *
     * @param sellChanges 该次换仓的卖出记录
     * @param buyVarietyKey 换入品种业务主键
     * @param bondSideways 转出的债券是否已达横盘阈值
     * @param familiarDays 换入品种已登记的天数，取不到返回 null
     * @param plan        警示参数
     * @return 命中的警示，未命中返回 null
     */
    public LessonAlert blindSwitch(HoldingChangeVO sellChanges, String buyVarietyKey, boolean bondSideways,
                                   Long familiarDays, LessonPlan plan) {
        if (plan == null || !plan.complete() || sellChanges == null || !bondSideways) {
            return null;
        }
        // 换入品种已登记够久说明是熟悉标的，不算盲目换仓
        if (familiarDays != null && familiarDays >= plan.getFamiliarDays()) {
            return null;
        }
        LessonAlert alert = base(LessonPattern.BLIND_SWITCH, sellChanges);
        alert.setTriggerDate(sellChanges.getOpenDate());
        alert.getRemarks().add("转出的债券基金已达横盘阈值，换入品种 "
                + (familiarDays == null ? "在库内无登记记录" : "登记仅 " + familiarDays + " 天")
                + "（阈值 " + plan.getFamiliarDays() + " 天），与「盲目转红利」的情形一致");
        return alert;
    }

    /**
     * 判定恐慌卖出：卖出当时该品种已连续亏损达天数且累计跌幅达阈值
     *
     * @param bars      该品种的前复权日线，按日期升序
     * @param sell      卖出变动记录
     * @param costPrice 该品种加权成本价
     * @param plan      警示参数
     * @return 命中的警示，未命中返回 null
     */
    public LessonAlert panicSell(List<MarketBar> bars, HoldingChangeVO sell, BigDecimal costPrice, LessonPlan plan) {
        if (plan == null || !plan.complete() || sell == null || sell.getOpenDate() == null
                || costPrice == null || costPrice.signum() <= 0) {
            return null;
        }
        List<MarketBar> before = barsBefore(bars, sell.getOpenDate());
        if (before.size() < plan.getPanicLossDays()) {
            return null;
        }
        // 连续亏损：收盘价低于成本价，且这个状态已持续够多天
        int lossDays = 0;
        for (int i = before.size() - 1; i >= 0; i--) {
            BigDecimal close = before.get(i).getClose();
            if (close == null || close.compareTo(costPrice) >= 0) {
                break;
            }
            lossDays++;
        }
        if (lossDays < plan.getPanicLossDays()) {
            return null;
        }
        BigDecimal latest = before.get(before.size() - 1).getClose();
        BigDecimal lossRate = latest.subtract(costPrice).multiply(BigDecimal.valueOf(HUNDRED))
                .divide(costPrice, RESULT_SCALE, RoundingMode.HALF_UP).negate();
        if (lossRate.compareTo(plan.getPanicLossRate()) < 0) {
            return null;
        }
        LessonAlert alert = base(LessonPattern.PANIC_SELL, sell);
        alert.setTriggerValue(lossRate);
        alert.setReferenceValue(costPrice);
        alert.setTriggerDate(sell.getOpenDate());
        alert.getRemarks().add("卖出前已连续 " + lossDays + " 个交易日低于成本价 " + plain(costPrice)
                + " 元，最新价 " + plain(latest) + " 元，累计亏损 " + plain(lossRate) + "%（阈值 "
                + plan.getPanicLossDays() + " 日且亏损不低于 " + plain(plan.getPanicLossRate()) + "%）");
        return alert;
    }

    /**
     * 取指定日期之前的日线，只用建仓或卖出当时可得的行情，避免用到未来数据
     *
     * @param bars 全部日线
     * @param date 截止日期，不含
     * @return 之前的日线
     */
    private List<MarketBar> barsBefore(List<MarketBar> bars, LocalDate date) {
        List<MarketBar> before = new ArrayList<>();
        if (bars == null || date == null) {
            return before;
        }
        for (MarketBar bar : bars) {
            if (bar.getDate() != null && bar.getDate().isBefore(date)) {
                before.add(bar);
            }
        }
        return before;
    }

    /**
     * 造一条基础警示，模式名与教训文案在这里补齐
     *
     * @param pattern 错误模式
     * @param change  对应的变动记录
     * @return 警示
     */
    private LessonAlert base(LessonPattern pattern, HoldingChangeVO change) {
        LessonAlert alert = new LessonAlert();
        alert.setPattern(pattern);
        alert.setPatternLabel(pattern.getLabel());
        alert.setLesson(LessonCatalog.textOf(pattern));
        alert.setVarietyKey(change.getVarietyKey());
        alert.setVarietyName(change.getVarietyName());
        return alert;
    }

    /**
     * 计算两个日期之间相差的自然日，任一为空返回 null
     *
     * @param from 起点
     * @param to   终点
     * @return 自然日数
     */
    public Long daysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    /**
     * 去掉多余尾零便于文案展示
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }
}
