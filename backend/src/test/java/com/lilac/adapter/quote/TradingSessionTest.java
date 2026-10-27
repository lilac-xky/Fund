package com.lilac.adapter.quote;

import com.lilac.adapter.model.MarketSymbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交易时段测试，A 股与港股时段不同，周末一律非交易
 */
class TradingSessionTest {

    private static final MarketSymbol A_SHARE = MarketSymbol.of("sh600519");
    private static final MarketSymbol HK_SHARE = MarketSymbol.of("hk00700");

    /**
     * A 股交易时段为 9:30-11:30 与 13:00-15:00
     */
    @Test
    @DisplayName("A 股交易时段判断")
    void aShareSessions() {
        LocalDateTime day = LocalDateTime.of(2026, 10, 27, 0, 0);

        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(9).withMinute(29))).isFalse();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(9).withMinute(30))).isTrue();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(11).withMinute(30))).isTrue();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(11).withMinute(31))).isFalse();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(12).withMinute(0))).isFalse();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(13).withMinute(0))).isTrue();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(14).withMinute(30))).isTrue();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(15).withMinute(1))).isFalse();
    }

    /**
     * 港股收盘晚于 A 股，A 股收盘后港股仍在交易
     */
    @Test
    @DisplayName("港股交易时段晚于 A 股")
    void hkSessionLaterThanAShare() {
        LocalDateTime day = LocalDateTime.of(2026, 10, 27, 0, 0);

        assertThat(TradingSession.isTrading(HK_SHARE, day.withHour(9).withMinute(30))).isTrue();
        assertThat(TradingSession.isTrading(HK_SHARE, day.withHour(12).withMinute(30))).isFalse();
        assertThat(TradingSession.isTrading(HK_SHARE, day.withHour(15).withMinute(30))).isTrue();
        assertThat(TradingSession.isTrading(A_SHARE, day.withHour(15).withMinute(30))).isFalse();
        assertThat(TradingSession.isTrading(HK_SHARE, day.withHour(16).withMinute(30))).isFalse();
    }

    /**
     * 周末即使时间落在时段内也不算交易
     */
    @Test
    @DisplayName("周末不是交易时段")
    void weekendNotTrading() {
        LocalDateTime saturday = LocalDateTime.of(2026, 10, 31, 10, 30);

        assertThat(TradingSession.isTrading(A_SHARE, saturday)).isFalse();
        assertThat(TradingSession.isTrading(HK_SHARE, saturday)).isFalse();
    }
}
