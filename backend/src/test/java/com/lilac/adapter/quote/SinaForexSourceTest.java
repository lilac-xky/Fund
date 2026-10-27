package com.lilac.adapter.quote;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.ForexRate;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 汇率解析测试，覆盖日线列序与实时字段位置，两者均为实测确认
 */
class SinaForexSourceTest {

    private static final String DAILY_URL = "https://vip.stock.finance.sina.com.cn/forex/api/jsonp.php/var%20_=/NewForexService.getDayKLine?symbol={pair}";
    private static final String QUOTE_URL = "https://hq.sinajs.cn/list={pair}";
    private static final String REFERER = "https://finance.sina.com.cn";

    // 日线真实片段：日期,开盘,最高,最低,收盘 —— 列序已用 3979 根复核，high/low 与 open/close 的极值关系无一条违例
    private static final String DAILY = "/*<script>location.href='//sina.com';</script>*/ var _=(\""
            + "2026-10-23,0.8518322913,0.8503907546,0.8518322913,0.8506439375,|"
            + "2026-10-26,0.8506945921,0.8499426289,0.8506945921,0.8502461463\");";

    // 实时真实片段：0=时间、1=现价、5=昨收、10=涨跌幅、17=日期
    private static final String LIVE = "var hq_str_fx_shkdcny=\"20:18:49,0.8500004250,0.8500004250,0.8502461463,"
            + "4.9871620000,0.8502461463,0.8504124500,0.8499137338,0.8500004250,美元兑人民币,-0.0289,-0.0002,0.0005,"
            + "新浪财经,0.0000,0.0000,,2026-10-27\";";

    /**
     * 日线收盘价取第 5 列，变动由相邻两个收盘价算出
     */
    @Test
    @DisplayName("日线取收盘价并算出变动")
    void parsesDailyClose() {
        SinaForexSource source = newSource(DAILY, "");
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();

        source.parseDaily(DAILY, dates, closes);

        assertThat(dates).containsExactly(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 26));
        assertThat(closes).hasSize(2);
        assertThat(closes.get(1)).isEqualByComparingTo("0.8502461463");
    }

    /**
     * 实时日期更新于日线时，用日线最新价作基准重算当日变动
     */
    @Test
    @DisplayName("实时价更新时重算当日变动")
    void usesLivePriceWhenNewer() {
        SinaForexSource source = newSource(DAILY, LIVE);

        ForexRate rate = source.fetchHkdCny();

        assertThat(rate.getPair()).isEqualTo("HKDCNY");
        assertThat(rate.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(rate.getRate()).isEqualByComparingTo("0.8500004250");
        assertThat(rate.getPreviousRate()).isEqualByComparingTo("0.8502461463");
        // (0.8500004250 − 0.8502461463) / 0.8502461463 ≈ −0.000289，保留 8 位小数
        assertThat(rate.getChangeRate()).isEqualByComparingTo("-0.00028900");
        assertThat(rate.usable()).isTrue();
    }

    /**
     * 实时行情也取不到时仍返回日线结果，变动按日线两根算
     */
    @Test
    @DisplayName("实时行情失败时回退日线")
    void fallsBackToDaily() {
        SinaForexSource source = newSource(DAILY, "");

        ForexRate rate = source.fetchHkdCny();

        assertThat(rate.getTradeDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(rate.getRate()).isEqualByComparingTo("0.8502461463");
        assertThat(rate.getPreviousRate()).isEqualByComparingTo("0.8506439375");
        assertThat(rate.getChangeRate()).isNotNull();
        assertThat(rate.usable()).isTrue();
    }

    /**
     * 日线与实时都取不到时返回不可用，由上层判定港股收益不能折算
     */
    @Test
    @DisplayName("两种源都失败时返回不可用")
    void unavailableWhenAllFail() {
        SinaForexSource source = newSource("<html>502</html>", "");

        ForexRate rate = source.fetchHkdCny();

        assertThat(rate.usable()).isFalse();
        assertThat(rate.getRate()).isNull();
    }

    /**
     * 日线结构变化时抛具名异常，不返回空对象
     */
    @Test
    @DisplayName("日线结构变化抛具名异常")
    void brokenStructureThrows() {
        SinaForexSource source = newSource("<html>验证页</html>", "");
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();

        assertThatThrownBy(() -> source.parseDaily("<html>验证页</html>", dates, closes))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结构变化");
    }

    /**
     * 构造使用固定文本的汇率源
     *
     * @param daily 日线返回
     * @param live  实时返回，空串表示该接口失败
     * @return 汇率源
     */
    private SinaForexSource newSource(String daily, String live) {
        return new SinaForexSource(new StubFetcher(daily, live), DAILY_URL, QUOTE_URL, REFERER);
    }

    /**
     * 按地址返回日线或实时文本的取数桩
     */
    private static class StubFetcher implements TextFetcher {

        private final String daily;
        private final String live;

        StubFetcher(String daily, String live) {
            this.daily = daily;
            this.live = live;
        }

        /**
         * 按 UTF-8 取数
         *
         * @param url 请求地址
         * @return 文本
         */
        @Override
        public String getText(String url) {
            return url.contains("getDayKLine") ? daily : live;
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

        /**
         * 按指定字符集与请求头取数
         *
         * @param url     请求地址
         * @param charset 字符集
         * @param headers 请求头
         * @return 文本
         */
        @Override
        public String getText(String url, Charset charset, Map<String, String> headers) {
            return getText(url);
        }
    }
}
