package com.lilac.adapter.eastmoney;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.ExchangeIdentity;
import com.lilac.adapter.model.ExchangeKind;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 交易场所身份回显测试，用真实腾讯返回片段验证类型标记解析
 */
class ExchangeIdentityAdapterTest {

    private static final int TYPE_FIELD_INDEX = 61;
    private static final int FIELD_COUNT = 88;
    private static final String URL = "https://qt.gtimg.cn/q=";

    /**
     * 只有类型标记为 ZS 的才算指数，同码股票与指数各自识别
     */
    @Test
    @DisplayName("同码返回指数与股票两个身份")
    void parseIndexAndStock() {
        ExchangeIdentityAdapter adapter = new ExchangeIdentityAdapter(new StubFetcher(realSample()), URL);

        Map<String, List<ExchangeIdentity>> resolved = adapter.resolve(List.of("000001", "510300"));

        List<ExchangeIdentity> ofFirst = resolved.get("000001");
        assertThat(ofFirst).extracting(ExchangeIdentity::kind)
                .containsExactly(ExchangeKind.INDEX, ExchangeKind.STOCK);
        assertThat(ofFirst.get(0).name()).isEqualTo("上证指数");
        assertThat(ofFirst.get(1).name()).isEqualTo("平安银行");
        assertThat(resolved.get("510300")).extracting(ExchangeIdentity::kind).containsExactly(ExchangeKind.ETF);
    }

    /**
     * 行情源没有返回该代码时返回空列表，由上层判定不存在
     */
    @Test
    @DisplayName("未命中返回空列表")
    void missingCodeReturnsEmpty() {
        ExchangeIdentityAdapter adapter = new ExchangeIdentityAdapter(new StubFetcher(realSample()), URL);

        assertThat(adapter.resolveAll("999999")).isEmpty();
    }

    /**
     * 行情不可用时抛异常，由消歧服务降级处理
     */
    @Test
    @DisplayName("取数失败抛业务异常")
    void fetchFailureThrows() {
        ExchangeIdentityAdapter adapter = new ExchangeIdentityAdapter(new StubFetcher(null), URL);

        assertThatThrownBy(() -> adapter.resolve(List.of("000001")))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 拼出与真实响应同构的 88 段行情串
     *
     * @return 行情文本
     */
    private String realSample() {
        return "v_sh000001=\"" + quote("1", "上证指数", "000001", "ZS") + "\";"
                + "v_sz000001=\"" + quote("51", "平安银行", "000001", "GP-A") + "\";"
                + "v_sh510300=\"" + quote("1", "沪深300ETF华泰柏瑞", "510300", "ETF") + "\";";
    }

    /**
     * 按腾讯字段位置拼一条行情，第 61 段为类型标记
     *
     * @param market 市场标记
     * @param name   名称
     * @param code   代码
     * @param marker 类型标记
     * @return 波浪线分隔的行情串
     */
    private String quote(String market, String name, String code, String marker) {
        List<String> fields = new ArrayList<>(FIELD_COUNT);
        fields.add(market);
        fields.add(name);
        fields.add(code);
        while (fields.size() < TYPE_FIELD_INDEX) {
            fields.add("0");
        }
        fields.add(marker);
        while (fields.size() < FIELD_COUNT) {
            fields.add("0");
        }
        return String.join("~", fields);
    }

    /**
     * 固定返回文本的取数桩，文本为 null 表示请求失败
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
            return require();
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
            return require();
        }

        /**
         * 模拟取数，null 时抛异常
         *
         * @return 文本
         */
        private String require() {
            if (text == null) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "外部数据源请求失败：模拟断网");
            }
            return text;
        }
    }
}
