package com.lilac.adapter.quote;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.MarketHistory;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 腾讯行情解析测试，字段位置按真实响应对齐，覆盖停牌识别与前复权日线
 */
class TencentQuoteSourceTest {

    private static final String QUOTE_URL = "https://qt.gtimg.cn/q=";
    private static final String KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param={symbol},day,,,{limit},qfq";
    private static final String HK_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/hkfqkline/get?param={symbol},day,,,{limit},qfq";
    private static final int FIELD_COUNT = 62;

    /**
     * 涨跌幅直接取第 32 段，与行情时间一起写入结果
     */
    @Test
    @DisplayName("实时行情取到涨跌幅与行情时间")
    void parseLiveQuote() {
        TencentQuoteSource source = newSource("");
        String text = quote("sh600519", "贵州茅台", "600519", "1355.90", "1349.98", "38669", "20261027161452", "0.44");

        MarketQuote quote = source.parseQuote(MarketSymbol.of("sh600519"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getName()).isEqualTo("贵州茅台");
        assertThat(quote.getChangePercent()).isEqualByComparingTo("0.44");
        assertThat(quote.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(quote.usable()).isTrue();
        assertThat(quote.getSource()).isEqualTo("tencent");
    }

    /**
     * 当日成交量为 0 时标为停牌，不能被当成 0% 参与加权
     */
    @Test
    @DisplayName("成交量为零识别为停牌")
    void suspendedWhenNoVolume() {
        TencentQuoteSource source = newSource("");
        String text = quote("sh600870", "退市海润", "600870", "0.60", "0.60", "0", "20261027090000", "0.00");

        MarketQuote quote = source.parseQuote(MarketSymbol.of("sh600870"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SUSPENDED);
        assertThat(quote.usable()).isFalse();
        assertThat(quote.getUnavailableReason()).contains("停牌");
    }

    /**
     * 北交所代码加 bj 前缀能取到行情，实测三个 920xxx 代码均可返回
     */
    @Test
    @DisplayName("北交所代码带 bj 前缀可取到行情")
    void bjPrefixSupported() {
        TencentQuoteSource source = newSource("");
        String text = quote("bj920599", "同力股份", "920599", "18.49", "18.98", "41361", "20261027153818", "-2.58");

        MarketQuote quote = source.parseQuote(MarketSymbol.of("bj920599"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getChangePercent()).isEqualByComparingTo("-2.58");
        assertThat(quote.usable()).isTrue();
    }

    /**
     * 港股时间用斜杠分隔，解析后落到同一交易日
     */
    @Test
    @DisplayName("港股时间格式可解析")
    void parseHkQuote() {
        TencentQuoteSource source = newSource("");
        String text = quote("hk00700", "腾讯控股", "00700", "385.200", "386.400", "16230914.0", "2026/10/27 16:08:21", "-0.31");

        MarketQuote quote = source.parseQuote(MarketSymbol.of("hk00700"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(quote.getChangePercent()).isEqualByComparingTo("-0.31");
    }

    /**
     * 前复权日线按日期升序解析，2026-07-28 的 -15.7% 是真跌而非除权跳空
     */
    @Test
    @DisplayName("前复权日线按日期升序解析")
    void parseQfqHistory() {
        String body = "{\"code\":0,\"data\":{\"sz300308\":{\"qfqday\":["
                + "[\"2026-07-27\",\"1019.826\",\"1075.746\",\"1078.806\",\"1002.136\",\"255903.000\"],"
                + "[\"2026-07-28\",\"999.866\",\"906.806\",\"1016.806\",\"898.796\",\"541051.000\"]]}}}";
        TencentQuoteSource source = newSource(body);

        MarketHistory history = source.parseHistory(MarketSymbol.of("sz300308"), body);

        assertThat(history.getBars()).hasSize(2);
        assertThat(history.latestDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(history.latestClose()).isEqualByComparingTo("906.806");
        assertThat(history.getAdjusted()).isTrue();
    }

    /**
     * 取到 HTML 验证页时抛异常，由上层切换到备用源
     */
    @Test
    @DisplayName("返回验证页时抛业务异常")
    void htmlValidationPageThrows() {
        TencentQuoteSource source = newSource("<html>验证页</html>");

        assertThatThrownBy(() -> source.fetchQuotes(List.of(MarketSymbol.of("sh600519"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证页");
    }

    /**
     * 按腾讯真实字段位置拼一条行情，第 30 段为时间、第 32 段为涨跌幅
     *
     * @param symbol   带前缀代码
     * @param name     名称
     * @param code     数字代码
     * @param price    现价
     * @param prev     昨收
     * @param volume   成交量
     * @param time     行情时间
     * @param percent  涨跌幅
     * @return 波浪线分隔的行情串
     */
    private String quote(String symbol, String name, String code, String price, String prev,
                         String volume, String time, String percent) {
        String[] fields = new String[FIELD_COUNT];
        java.util.Arrays.fill(fields, "");
        fields[1] = name;
        fields[2] = code;
        fields[3] = price;
        fields[4] = prev;
        fields[5] = price;
        fields[6] = volume;
        fields[9] = price;
        fields[30] = time;
        fields[31] = "0.00";
        fields[32] = percent;
        return "v_" + symbol + "=\"" + String.join("~", fields) + "\";";
    }

    /**
     * 构造使用固定文本的腾讯源
     *
     * @param text 固定返回文本
     * @return 腾讯源
     */
    private TencentQuoteSource newSource(String text) {
        return new TencentQuoteSource(new StubFetcher(text), QUOTE_URL, KLINE_URL, HK_KLINE_URL);
    }

    /**
     * 固定返回文本的取数桩
     */
    private static class StubFetcher implements TextFetcher {

        private final String text;

        StubFetcher(String text) {
            this.text = text;
        }

        /**
         * 按 UTF-8 取数
         *
         * @param url 请求地址
         * @return 文本
         */
        @Override
        public String getText(String url) {
            if (text == null) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "模拟断网");
            }
            return text;
        }

        /**
         * 按指定字符集取数
         *
         * @param url     请求地址
         * @param charset 字符集
         * @return 文本
         */
        @Override
        public String getText(String url, Charset charset) {
            return getText(url);
        }
    }
}
