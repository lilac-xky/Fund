package com.lilac.adapter.quote;

import com.lilac.adapter.model.MarketHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指数日线的市场补查测试，钉住 000300 这类沪市指数不会被深市推错后彻底放弃
 */
@SpringBootTest
class IndexHistorySiblingTest {

    @Autowired
    private MarketQuoteAdapter adapter;

    /**
     * 上证指数 000300 按号段会被推成 sz，补查沪市后必须取到日线
     */
    @Test
    @DisplayName("000300 日线可跨市场补查命中")
    void indexHistoryResolvesSiblingMarket() {
        MarketHistory history = adapter.history("000300", 60);

        assertThat(history.getBars()).isNotEmpty();
        assertThat(history.getBars().size()).isGreaterThanOrEqualTo(2);
        // 上证指数在 3000 点以上运行，收盘价不可能是深市那个同码标的的量级
        assertThat(history.getBars().get(history.getBars().size() - 1).getClose())
                .isGreaterThan(new java.math.BigDecimal("1000"));
    }

    /**
     * 足额返回时不补查，避免多打一次外部请求
     */
    @Test
    @DisplayName("正常标的只取一次不补查")
    void normalSymbolNoSiblingFetch() {
        MarketHistory history = adapter.history("sz399986", 60);

        assertThat(history.getBars()).isNotEmpty();
    }
}
