package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基金档案页解析测试，用真实 HTML 片段验证跟踪标的提取
 */
class FundDetailAdapterTest {

    private static final String SAMPLE = "<table class=\"info w790\"><tr><th>业绩比较基准</th><td>中证红利低波动指数收益率</td>"
            + "<th>跟踪标的</th><td>中证红利低波动指数</td></tr></table>";
    private static final String NO_TRACK = "<th>跟踪标的</th><td>该基金无跟踪标的</td>";

    /**
     * 从档案页取出跟踪标的名称
     */
    @Test
    @DisplayName("提取跟踪标的")
    void extractTrackIndexName() {
        FundDetailAdapter adapter = new FundDetailAdapter(new StubFetcher(SAMPLE), "https://example.com/{code}.html");

        assertThat(adapter.fetchTrackIndexName("512890")).contains("中证红利低波动指数");
    }

    /**
     * 无跟踪标的与结构缺失都返回空，不抛异常
     */
    @Test
    @DisplayName("无跟踪标的返回空")
    void noTrackIndexReturnsEmpty() {
        FundDetailAdapter adapter = new FundDetailAdapter(new StubFetcher(NO_TRACK), "https://example.com/{code}.html");
        assertThat(adapter.fetchTrackIndexName("110022")).isEmpty();

        FundDetailAdapter broken = new FundDetailAdapter(new StubFetcher("<html></html>"),
                "https://example.com/{code}.html");
        assertThat(broken.fetchTrackIndexName("110022")).isEmpty();
    }

    /**
     * 固定返回 HTML 的取数桩
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
         * @return HTML
         */
        @Override
        public String getText(String url) {
            return text;
        }

        /**
         * 按指定字符集取数
         *
         * @param url     请求地址
         * @param charset 字符集
         * @return HTML
         */
        @Override
        public String getText(String url, Charset charset) {
            return text;
        }
    }
}
