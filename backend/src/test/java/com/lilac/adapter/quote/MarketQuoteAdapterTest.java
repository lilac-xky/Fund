package com.lilac.adapter.quote;

import com.lilac.adapter.model.MarketBar;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.adapter.probe.SourceEventRecorder;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 行情编排测试，核心是「单源失效自动切换」与「单只失败不影响同批」
 */
class MarketQuoteAdapterTest {

    private FakeSource tencent;
    private FakeSource sina;
    private MarketQuoteAdapter adapter;
    private final InMemoryCache cache = new InMemoryCache();
    private final SourceEventRecorder recorder = mock(SourceEventRecorder.class);

    /**
     * 按腾讯在前、新浪在后的顺序装配编排器
     *
     * @throws Exception 反射注入失败
     */
    @BeforeEach
    void setUp() throws Exception {
        tencent = new FakeSource("tencent");
        sina = new FakeSource("sina");
        adapter = new MarketQuoteAdapter(List.of(tencent, sina), cache, null, recorder);
        setField("batchSize", 60);
        setField("historyLimit", 60);
    }

    /**
     * 腾讯整体被限流时自动切到新浪，功能不中断
     */
    @Test
    @DisplayName("主源失效自动切换备用源")
    void fallsBackWhenPrimaryLimited() {
        tencent.throwOnQuotes = new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯行情返回验证页，疑似被限流");
        sina.quotes.put("sh600519", okQuote("sh600519", "0.44"));

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh600519"));

        assertThat(result.get("sh600519").usable()).isTrue();
        assertThat(result.get("sh600519").getSource()).isEqualTo("sina");
        assertThat(sina.calls).hasSize(1);
    }

    /**
     * 主源正常时不再请求备用源，避免无故增加上游压力
     */
    @Test
    @DisplayName("主源可用时不请求备用源")
    void noFallbackWhenPrimaryWorks() {
        tencent.quotes.put("sh600519", okQuote("sh600519", "0.44"));

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh600519"));

        assertThat(result.get("sh600519").getSource()).isEqualTo("tencent");
        assertThat(sina.calls).isEmpty();
    }

    /**
     * 主源只缺其中一只标的，备用源补上这一只，其余仍用主源
     */
    @Test
    @DisplayName("单只缺失由备用源补齐")
    void fillsMissingSingleSymbol() {
        tencent.quotes.put("sh600519", okQuote("sh600519", "0.44"));
        tencent.quotes.put("sz000001", noDataQuote("sz000001"));
        sina.quotes.put("sz000001", okQuote("sz000001", "1.50"));

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh600519", "sz000001"));

        assertThat(result.get("sh600519").getSource()).isEqualTo("tencent");
        assertThat(result.get("sz000001").getSource()).isEqualTo("sina");
        assertThat(result.get("sz000001").usable()).isTrue();
    }

    /**
     * 停牌是标的自身状态，换源也不会变成有成交，不应触发备用源
     */
    @Test
    @DisplayName("停牌不触发换源")
    void suspendedNotRetriedOnBackup() {
        MarketQuote suspended = okQuote("sh600870", "0.00");
        suspended.setStatus(QuoteStatus.SUSPENDED);
        suspended.setUnavailableReason("当日无成交（停牌或未交易）");
        tencent.quotes.put("sh600870", suspended);

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh600870"));

        assertThat(result.get("sh600870").getStatus()).isEqualTo(QuoteStatus.SUSPENDED);
        assertThat(sina.calls).isEmpty();
    }

    /**
     * 两个源都取不到时，保留「数据源无该标的」而不是笼统的不可用，920xxx 北交所属于这一类
     */
    @Test
    @DisplayName("两源皆无数据时给出明确状态")
    void noDataWhenAllSourcesMiss() {
        tencent.quotes.put("bj920599", noDataQuote("bj920599"));
        sina.quotes.put("bj920599", noDataQuote("bj920599"));

        Map<String, MarketQuote> result = adapter.quotes(List.of("bj920599"));

        MarketQuote quote = result.get("bj920599");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.NO_DATA);
        assertThat(quote.usable()).isFalse();
        assertThat(quote.getChangePercent()).isNull();
    }

    /**
     * 单只解析异常不影响同批其他标的
     */
    @Test
    @DisplayName("单只失败不影响同批")
    void singleFailureDoesNotBreakBatch() {
        tencent.brokenSymbols.add("sz000002");
        tencent.quotes.put("sh600519", okQuote("sh600519", "0.44"));
        sina.quotes.put("sz000002", okQuote("sz000002", "-1.20"));

        Map<String, MarketQuote> result = adapter.quotes(List.of("sh600519", "sz000002"));

        assertThat(result.get("sh600519").usable()).isTrue();
        assertThat(result.get("sz000002").usable()).isTrue();
        assertThat(result.get("sz000002").getSource()).isEqualTo("sina");
    }

    /**
     * 超过批大小时拆成多次请求，单次请求不超限
     */
    @Test
    @DisplayName("超过批大小自动拆分请求")
    void splitsLargeBatch() throws Exception {
        setField("batchSize", 10);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String code = "sh60" + String.format("%04d", i);
            codes.add(code);
            tencent.quotes.put(code, okQuote(code, "0.10"));
        }

        adapter.quotes(codes);

        assertThat(tencent.calls).hasSize(3);
        assertThat(tencent.calls).extracting(List::size).containsExactly(10, 10, 5);
    }

    /**
     * 同一标的重复传入只请求一次
     */
    @Test
    @DisplayName("重复代码只请求一次")
    void deduplicatesCodes() {
        tencent.quotes.put("sh600519", okQuote("sh600519", "0.44"));

        adapter.quotes(List.of("sh600519", "600519", "sh600519"));

        assertThat(tencent.calls).hasSize(1);
        assertThat(tencent.calls.get(0)).hasSize(1);
    }

    /**
     * 可用的行情进入缓存，第二次调用不再走上游
     */
    @Test
    @DisplayName("可用行情命中缓存后不再请求上游")
    void usesCacheOnSecondCall() {
        tencent.quotes.put("sh600519", okQuote("sh600519", "0.44"));
        adapter.quotes(List.of("sh600519"));
        int firstCalls = tencent.calls.size();

        adapter.quotes(List.of("sh600519"));

        assertThat(tencent.calls).hasSize(firstCalls);
    }

    /**
     * 主源日线失败时退到备用源，且返回对象标注不复权
     */
    @Test
    @DisplayName("日线退到备用源时标注不复权")
    void historyFallsBackAndMarksUnadjusted() {
        tencent.throwOnHistory = new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯日线返回验证页");
        sina.history = historyOf("sh600519", false);

        MarketHistory history = adapter.history("sh600519", 5);

        assertThat(history.getBars()).hasSize(2);
        assertThat(history.getAdjusted()).isFalse();
        assertThat(sina.historyCalls).isEqualTo(1);
    }

    /**
     * 全部日线源失败时返回空序列而不是抛异常，调用方据根数判断
     */
    @Test
    @DisplayName("日线全部失败返回空序列")
    void historyAllFailReturnsEmpty() {
        tencent.throwOnHistory = new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "腾讯日线失败");
        sina.throwOnHistory = new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "新浪日线失败");

        MarketHistory history = adapter.history("sh600519", 5);

        assertThat(history.getBars()).isEmpty();
        assertThat(history.getSource()).isEqualTo("none");
    }

    /**
     * 港股收益按 (1 + 标的)(1 + 汇率) − 1 复利折算
     */
    @Test
    @DisplayName("港股收益按复利口径折算")
    void cnyReturnCompounds() {
        BigDecimal result = MarketQuoteAdapter.toCnyReturn(new BigDecimal("2.00"), new BigDecimal("0.005"));

        // (1 + 0.02) × (1 + 0.005) − 1 = 0.0251 → 2.51 个百分点
        assertThat(result).isEqualByComparingTo("2.5100");
    }

    /**
     * 汇率缺失时按 0 处理，不丢弃标的本身的涨跌
     */
    @Test
    @DisplayName("汇率缺失时按零处理")
    void cnyReturnWithoutForex() {
        assertThat(MarketQuoteAdapter.toCnyReturn(new BigDecimal("1.00"), null))
                .isEqualByComparingTo("1.0000");
    }

    /**
     * 反射写入配置项
     *
     * @param name  字段名
     * @param value 值
     * @throws Exception 反射失败
     */
    private void setField(String name, Object value) throws Exception {
        Field field = MarketQuoteAdapter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adapter, value);
    }

    /**
     * 构造一条可用行情
     *
     * @param symbol  带前缀代码
     * @param percent 涨跌幅
     * @return 行情
     */
    private MarketQuote okQuote(String symbol, String percent) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(MarketSymbol.of(symbol));
        quote.setChangePercent(new BigDecimal(percent));
        quote.setPrice(new BigDecimal("10.00"));
        quote.setPreviousClose(new BigDecimal("9.90"));
        quote.setTradeDate(LocalDate.now());
        quote.setStatus(QuoteStatus.OK);
        quote.setSource("stub");
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.TRUE);
        return quote;
    }

    /**
     * 构造一条无行情结果
     *
     * @param symbol 带前缀代码
     * @return 无行情
     */
    private MarketQuote noDataQuote(String symbol) {
        return MarketQuote.unavailable(MarketSymbol.of(symbol), QuoteStatus.NO_DATA, "数据源无该标的");
    }

    /**
     * 构造日线序列
     *
     * @param symbol   带前缀代码
     * @param adjusted 是否前复权
     * @return 日线
     */
    private MarketHistory historyOf(String symbol, boolean adjusted) {
        MarketHistory history = new MarketHistory();
        history.setSymbol(MarketSymbol.of(symbol));
        history.setBars(List.of(
                new MarketBar(LocalDate.of(2026, 10, 24), new BigDecimal("9.80")),
                new MarketBar(LocalDate.of(2026, 10, 27), new BigDecimal("9.90"))));
        history.setAdjusted(adjusted);
        history.setSource("stub");
        history.setFetchDate(LocalDate.now());
        return history;
    }

    /**
     * 可编排的数据源桩，可配置整体失败与逐只缺失
     */
    private static class FakeSource implements QuoteSource {

        private final String name;
        private final Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        private final List<String> brokenSymbols = new ArrayList<>();
        private final List<List<MarketSymbol>> calls = new ArrayList<>();
        private BusinessException throwOnQuotes;
        private BusinessException throwOnHistory;
        private MarketHistory history;
        private int historyCalls;

        FakeSource(String name) {
            this.name = name;
        }

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
         * 桩数据源对所有标的都声称支持
         *
         * @param symbol 行情标的
         * @return 始终 true
         */
        @Override
        public boolean supports(MarketSymbol symbol) {
            return true;
        }

        /**
         * 模拟批量取行情，单只缺失或异常按实际情况返回或抛出
         *
         * @param symbols 标的列表
         * @return 行情映射
         */
        @Override
        public Map<String, MarketQuote> fetchQuotes(List<MarketSymbol> symbols) {
            calls.add(symbols);
            if (throwOnQuotes != null) {
                throw throwOnQuotes;
            }
            Map<String, MarketQuote> result = new LinkedHashMap<>();
            for (MarketSymbol symbol : symbols) {
                if (brokenSymbols.contains(symbol.symbol())) {
                    continue;
                }
                MarketQuote quote = quotes.get(symbol.symbol());
                if (quote != null) {
                    MarketQuote copy = new MarketQuote();
                    copy.setSymbol(symbol);
                    copy.setName(quote.getName());
                    copy.setPrice(quote.getPrice());
                    copy.setPreviousClose(quote.getPreviousClose());
                    copy.setChangePercent(quote.getChangePercent());
                    copy.setTradeDate(quote.getTradeDate());
                    copy.setStatus(quote.getStatus());
                    copy.setUnavailableReason(quote.getUnavailableReason());
                    copy.setSource(name);
                    copy.setAdjusted(quote.getAdjusted());
                    copy.setRealtime(quote.getRealtime());
                    result.put(symbol.symbol(), copy);
                }
            }
            return result;
        }

        /**
         * 模拟取日线
         *
         * @param symbol 行情标的
         * @param limit  根数
         * @return 日线
         */
        @Override
        public MarketHistory fetchHistory(MarketSymbol symbol, int limit) {
            historyCalls++;
            if (throwOnHistory != null) {
                throw throwOnHistory;
            }
            return history;
        }
    }

    /**
     * 内存缓存桩，避免测试依赖真实 Redis
     */
    private static class InMemoryCache extends QuoteCache {

        private final Map<String, MarketQuote> store = new LinkedHashMap<>();

        /**
         * 写缓存
         *
         * @param quote 行情
         */
        @Override
        public void save(MarketQuote quote) {
            store.put(quote.getSymbol().symbol(), quote);
        }

        /**
         * 读缓存
         *
         * @param symbol 带前缀代码
         * @return 行情
         */
        @Override
        public Optional<MarketQuote> read(String symbol) {
            return Optional.ofNullable(store.get(symbol));
        }
    }
}
