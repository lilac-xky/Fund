package com.lilac.adapter.probe;

import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.quote.MarketQuoteAdapter;
import com.lilac.adapter.quote.QuoteCache;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.quote.QuoteSource;
import com.lilac.domain.vo.DegradationRowVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据源失效降级测试，核心是「单源失效不影响其余源」与「结构变化有可定位的告警」
 */
class SourceDegradationTest {

    private MarketQuoteAdapter adapter;
    private QuoteCache cache;
    private SourceEventRecorder recorder;
    private FakeSource first;
    private FakeSource second;

    /**
     * 装配两个可独立制造故障的数据源
     */
    @BeforeEach
    void setUp() {
        cache = mock(QuoteCache.class);
        when(cache.read(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        recorder = mock(SourceEventRecorder.class);
        first = new FakeSource("tencent");
        second = new FakeSource("sina");
        adapter = new MarketQuoteAdapter(List.of(first, second), cache, null, recorder);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "batchSize", 60);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "historyLimit", 60);
    }

    /**
     * 主源整体失效时自动切到备用源，标的数据不丢
     */
    @Test
    @DisplayName("主源失效时自动切到备用源")
    void primaryFailureFallsBack() {
        first.failQuotes = true;
        second.setQuotes("sh510300", "4.111", "0.50");

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh510300"));

        MarketQuote quote = result.get("sh510300");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getSource()).isEqualTo("sina");
        // 失败留痕要能定位到是哪个源出的问题
        verify(recorder).failure(ArgumentMatchers.eq("tencent"), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.any());
    }

    /**
     * 两个源都失效时返回不可用而不是编造数字
     */
    @Test
    @DisplayName("全部源失效时返回不可用")
    void allSourcesFailYieldsUnavailable() {
        first.failQuotes = true;
        second.failQuotes = true;

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh510300"));

        MarketQuote quote = result.get("sh510300");
        assertThat(quote.usable()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.UNAVAILABLE);
    }

    /**
     * 上游结构变化（抛 DATA_SOURCE_BROKEN）要按 STRUCTURE 留痕，便于判断是改版
     */
    @Test
    @DisplayName("上游结构变化按 STRUCTURE 留痕")
    void structureChangeMarkedAsStructure() {
        first.structureBroken = true;

        adapter.quotes(List.of("sh510300"));

        verify(recorder).failure(ArgumentMatchers.eq("tencent"), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(SourceEventRecorder.FailureKind.STRUCTURE), ArgumentMatchers.any());
    }

    /**
     * 被限流时按 THROTTLED 留痕，与结构变化区分开
     */
    @Test
    @DisplayName("被限流按 THROTTLED 留痕")
    void throttledMarkedAsThrottled() {
        first.throttled = true;

        adapter.quotes(List.of("sh510300"));

        verify(recorder).failure(ArgumentMatchers.eq("tencent"), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(SourceEventRecorder.FailureKind.THROTTLED), ArgumentMatchers.any());
    }

    /**
     * 成功取数也要留痕，否则算不出失败率
     */
    @Test
    @DisplayName("成功取数也留痕")
    void successRecorded() {
        first.setQuotes("sh510300", "4.111", "0.50");

        adapter.quotes(List.of("sh510300"));

        verify(recorder).success("tencent", "sh510300");
    }

    /**
     * 日线主源失效时切到备用源，并标注复权口径
     */
    @Test
    @DisplayName("日线主源失效时切到备用源")
    void historyFallsBack() {
        first.failHistory = true;
        second.setHistory("sh510300");

        MarketHistory history = adapter.history("sh510300", 20);

        assertThat(history.getSource()).isEqualTo("sina");
        assertThat(history.getAdjusted()).isFalse();
        assertThat(history.getBars()).isNotEmpty();
    }

    /**
     * 降级矩阵必须覆盖全部数据源且每条都有提示方式
     */
    @Test
    @DisplayName("降级矩阵覆盖各源且都有提示方式")
    void matrixComplete() {
        List<DegradationRowVO> rows = DegradationMatrix.rows();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getSource()).isNotBlank();
            assertThat(row.getPurpose()).isNotBlank();
            assertThat(row.getNotice()).isNotBlank();
            assertThat(row.getAffected()).isNotEmpty();
        });
        assertThat(rows).anyMatch(row -> "tencent".equals(row.getSource()) && row.getHasFallback());
        assertThat(rows).anyMatch(row -> "csindex".equals(row.getSource()) && !row.getHasFallback());
    }

    /**
     * 可独立制造故障的行情源
     */
    private static class FakeSource implements QuoteSource {

        private final String name;
        private boolean failQuotes;
        private boolean failHistory;
        private boolean structureBroken;
        private boolean throttled;
        private final Map<String, MarketQuote> quotes = new LinkedHashMap<>();

        FakeSource(String name) {
            this.name = name;
        }

        /**
         * 设置该源能取到的行情
         *
         * @param symbol  完整代码
         * @param price   现价
         * @param percent 涨跌幅
         */
        void setQuotes(String symbol, String price, String percent) {
            MarketQuote quote = new MarketQuote();
            quote.setSymbol(MarketSymbol.of(symbol));
            quote.setName("测试标的");
            quote.setPrice(new BigDecimal(price));
            quote.setPreviousClose(new BigDecimal(price));
            quote.setChangePercent(new BigDecimal(percent));
            quote.setTradeDate(LocalDate.now());
            quote.setSource(name);
            quote.setStatus(QuoteStatus.OK);
            quote.setRealtime(Boolean.TRUE);
            quotes.put(symbol, quote);
        }

        /**
         * 设置该源能取到的日线
         *
         * @param symbol 完整代码
         */
        void setHistory(String symbol) {
            this.historySymbol = symbol;
        }

        private String historySymbol;

        /**
         * 数据源名称
         *
         * @return 名称
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * 始终支持
         *
         * @param symbol 标的
         * @return true
         */
        @Override
        public boolean supports(MarketSymbol symbol) {
            return true;
        }

        /**
         * 按故障开关返回行情或抛异常
         *
         * @param symbols 标的列表
         * @return 行情
         */
        @Override
        public Map<String, MarketQuote> fetchQuotes(List<MarketSymbol> symbols) {
            if (throttled) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯行情返回验证页，疑似被限流");
            }
            if (structureBroken) {
                throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN, "字段数不足");
            }
            if (failQuotes) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "模拟断网");
            }
            return quotes;
        }

        /**
         * 按故障开关返回日线或抛异常
         *
         * @param symbol 标的
         * @param limit  根数
         * @return 日线
         */
        @Override
        public MarketHistory fetchHistory(MarketSymbol symbol, int limit) {
            if (failHistory) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "模拟日线断网");
            }
            MarketHistory history = new MarketHistory();
            history.setSymbol(symbol);
            history.setBars(List.of(
                    new MarketBar(LocalDate.now().minusDays(1), new BigDecimal("4.0")),
                    new MarketBar(LocalDate.now(), new BigDecimal("4.1"))));
            history.setAdjusted(Boolean.FALSE);
            history.setSource(name);
            history.setFetchDate(LocalDate.now());
            return history;
        }
    }
}
