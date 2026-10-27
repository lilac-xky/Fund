package com.lilac.adapter.quote;

import com.lilac.adapter.model.MarketSymbol;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 交易时段判断，区分 A 股与港股时段，非交易时段的数据须标为上一交易日
 */
public final class TradingSession {

    private static final LocalTime A_MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime A_MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime A_AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime A_AFTERNOON_CLOSE = LocalTime.of(15, 0);
    private static final LocalTime HK_MORNING_CLOSE = LocalTime.of(12, 0);
    private static final LocalTime HK_AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime HK_AFTERNOON_CLOSE = LocalTime.of(16, 10);
    private static final LocalTime HK_MORNING_OPEN = LocalTime.of(9, 30);

    private TradingSession() {
    }

    /**
     * 判断某个时间点是否为该标的的交易时段，周末一律不是
     *
     * @param symbol 行情标的
     * @param now    判断时间
     * @return 交易时段内返回 true
     */
    public static boolean isTrading(MarketSymbol symbol, LocalDateTime now) {
        LocalDate day = now.toLocalDate();
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (symbol.hongKong()) {
            return inRange(time, HK_MORNING_OPEN, HK_MORNING_CLOSE) || inRange(time, HK_AFTERNOON_OPEN, HK_AFTERNOON_CLOSE);
        }
        return inRange(time, A_MORNING_OPEN, A_MORNING_CLOSE) || inRange(time, A_AFTERNOON_OPEN, A_AFTERNOON_CLOSE);
    }

    /**
     * 判断时间是否落在闭开区间内
     *
     * @param time  判断时间
     * @param start 开始
     * @param end   结束
     * @return 区间内返回 true
     */
    private static boolean inRange(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}
