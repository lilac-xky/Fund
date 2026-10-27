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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新浪行情解析测试，覆盖 A 股、港股字段布局与不复权日线
 */
class SinaQuoteSourceTest {

    private static final String QUOTE_URL = "https://hq.sinajs.cn/list=";
    private static final String KLINE_URL = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol={symbol}&scale=240&ma=no&datalen={limit}";
    private static final String REFERER = "https://finance.sina.com.cn";

    /**
     * A 股字段：名称、开盘、昨收、现价…成交量…日期、时间，共 33 段
     */
    @Test
    @DisplayName("A 股行情按字段位置解析")
    void parseAShareQuote() {
        SinaQuoteSource source = newSource("");
        String text = "var hq_str_sh600519=\"" + aShare("贵州茅台", "1343.880", "1349.980", "1355.900", "3866914") + "\";";

        MarketQuote quote = source.parseQuote(MarketSymbol.of("sh600519"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getName()).isEqualTo("贵州茅台");
        assertThat(quote.getPrice()).isEqualByComparingTo("1355.900");
        assertThat(quote.getPreviousClose()).isEqualByComparingTo("1349.980");
        assertThat(quote.getChangePercent()).isEqualByComparingTo("0.4385");
        assertThat(quote.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(quote.getSource()).isEqualTo("sina");
    }

    /**
     * 港股字段顺序与 A 股不同：英文名、中文名、开盘、昨收…现价、涨跌、涨跌幅
     */
    @Test
    @DisplayName("港股行情按独立字段布局解析")
    void parseHkQuote() {
        SinaQuoteSource source = newSource("");
        String text = "var hq_str_hk00700=\"TENCENT,腾讯控股,387.000,386.400,390.600,383.600,385.200,-1.200,-0.311,"
                + "385.20001,385.39999,6270746749,16230914,0.000,0.000,656.353,378.000,2026/10/27,16:08\";";

        MarketQuote quote = source.parseQuote(MarketSymbol.of("hk00700"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quote.getName()).isEqualTo("腾讯控股");
        assertThat(quote.getPrice()).isEqualByComparingTo("385.200");
        assertThat(quote.getChangePercent()).isEqualByComparingTo("-0.311");
        assertThat(quote.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    /**
     * 成交量为 0 标为停牌，不参与加权
     */
    @Test
    @DisplayName("成交量为零识别为停牌")
    void suspendedWhenNoVolume() {
        SinaQuoteSource source = newSource("");
        String text = "var hq_str_sh600870=\"" + aShare("退市海润", "0.000", "0.600", "0.600", "0") + "\";";

        MarketQuote quote = source.parseQuote(MarketSymbol.of("sh600870"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SUSPENDED);
        assertThat(quote.usable()).isFalse();
    }

    /**
     * 空串表示该标的在新浪无行情
     */
    @Test
    @DisplayName("空行情串返回无数据")
    void emptyQuoteReturnsNoData() {
        SinaQuoteSource source = newSource("");
        String text = "var hq_str_sh999999=\"\";";

        MarketQuote quote = source.parseQuote(MarketSymbol.of("sh999999"), text);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.NO_DATA);
        assertThat(quote.usable()).isFalse();
    }

    /**
     * 新浪日线不复权，返回对象必须标 adjusted=false，调用方据此判断口径
     */
    @Test
    @DisplayName("新浪日线标注不复权")
    void historyMarkedUnadjusted() {
        String body = "[{\"day\":\"2026-10-23\",\"open\":\"1311.840\",\"high\":\"1343.880\",\"low\":\"1310.000\","
                + "\"close\":\"1335.000\",\"volume\":\"5768593\"},"
                + "{\"day\":\"2026-10-27\",\"open\":\"1343.880\",\"high\":\"1357.000\",\"low\":\"1338.010\","
                + "\"close\":\"1355.900\",\"volume\":\"3866914\"}]";
        SinaQuoteSource source = newSource(body);

        MarketHistory history = source.parseHistory(MarketSymbol.of("sh600519"), body);

        assertThat(history.getBars()).hasSize(2);
        assertThat(history.getAdjusted()).isFalse();
        assertThat(history.latestClose()).isEqualByComparingTo("1355.900");
    }

    /**
     * 新浪返回 null 字符串时抛结构变化，不返回空对象
     */
    @Test
    @DisplayName("日线返回 null 时抛结构变化")
    void nullHistoryThrows() {
        SinaQuoteSource source = newSource("null");

        assertThatThrownBy(() -> source.parseHistory(MarketSymbol.of("sh600519"), "null"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非数组");
    }

    /**
     * 缺少 Referer 时新浪返回 Forbidden，整体失败交给上层换源
     */
    @Test
    @DisplayName("被拒绝时抛业务异常")
    void forbiddenThrows() {
        SinaQuoteSource source = newSource("Forbidden");

        assertThatThrownBy(() -> source.fetchQuotes(List.of(MarketSymbol.of("sh600519"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝访问");
    }

    /**
     * 批量请求时每个标的都能独立解析
     */
    @Test
    @DisplayName("批量返回中逐只解析")
    void parseBatch() {
        String text = "var hq_str_sh600519=\"" + aShare("贵州茅台", "1343.880", "1349.980", "1355.900", "3866914") + "\";"
                + "var hq_str_sh999999=\"\";"
                + "var hq_str_sz000001=\"" + aShare("平安银行", "11.270", "11.300", "11.470", "152929155") + "\";";
        SinaQuoteSource source = newSource(text);

        Map<String, MarketQuote> quotes = source.fetchQuotes(
                List.of(MarketSymbol.of("sh600519"), MarketSymbol.of("sh999999"), MarketSymbol.of("sz000001")));

        assertThat(quotes).containsKeys("sh600519", "sh999999", "sz000001");
        assertThat(quotes.get("sh600519").getStatus()).isEqualTo(QuoteStatus.OK);
        assertThat(quotes.get("sh999999").getStatus()).isEqualTo(QuoteStatus.NO_DATA);
    }

    /**
     * 按新浪真实字段位置拼一条 A 股行情，第 8 段为成交量、第 30 段为日期、第 31 段为时间
     *
     * @param name     名称
     * @param open     开盘价
     * @param prev     昨收
     * @param price    现价
     * @param volume   成交量
     * @return 逗号分隔的字段串
     */
    private String aShare(String name, String open, String prev, String price, String volume) {
        String[] fields = new String[33];
        java.util.Arrays.fill(fields, "0");
        fields[0] = name;
        fields[1] = open;
        fields[2] = prev;
        fields[3] = price;
        fields[4] = "1357.000";
        fields[5] = "1338.010";
        fields[8] = volume;
        fields[9] = "5226798679.000";
        fields[30] = "2026-10-27";
        fields[31] = "15:34:59";
        fields[32] = "00";
        return String.join(",", fields);
    }

    /**
     * 构造使用固定文本的新浪源
     *
     * @param text 固定返回文本
     * @return 新浪源
     */
    private SinaQuoteSource newSource(String text) {
        return new SinaQuoteSource(new StubFetcher(text), QUOTE_URL, KLINE_URL, REFERER);
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
         * 按指定字符集与请求头取数
         *
         * @param url     请求地址
         * @param charset 字符集
         * @param headers 请求头
         * @return 文本
         */
        @Override
        public String getText(String url, Charset charset, java.util.Map<String, String> headers) {
            return getText(url);
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
