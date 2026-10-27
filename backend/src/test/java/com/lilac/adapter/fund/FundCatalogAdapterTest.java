package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundCatalogItem;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基金全集解析测试，用真实返回片段验证去前缀与列序
 */
class FundCatalogAdapterTest {

    private static final String SAMPLE = "\uFEFFvar r = [[\"000001\",\"HXCZHH\",\"华夏成长混合\",\"混合型-灵活\","
            + "\"HUAXIACHENGZHANGHUNHE\"],[\"510300\",\"HS300ETFHTBR\",\"沪深300ETF华泰柏瑞\",\"指数型-股票\","
            + "\"HUSHEN300ETFHUATAIBAIRUI\"]];";

    /**
     * 真实返回片段能被解析出行，列序与上游一致
     */
    @Test
    @DisplayName("去 JS 前缀后按列序解析基金全集")
    void parseRealSample() {
        FundCatalogAdapter adapter = new FundCatalogAdapter(new StubFetcher(SAMPLE), "https://example.com/fund.js");

        List<FundCatalogItem> items = adapter.parse(SAMPLE);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getCode()).isEqualTo("000001");
        assertThat(items.get(0).getName()).isEqualTo("华夏成长混合");
        assertThat(items.get(0).getType()).isEqualTo("混合型-灵活");
        assertThat(items.get(1).getCode()).isEqualTo("510300");
        assertThat(items.get(1).getName()).isEqualTo("沪深300ETF华泰柏瑞");
        assertThat(adapter.fetchAll()).hasSize(2);
    }

    /**
     * 结构变化时抛业务异常，而不是返回空集合
     */
    @Test
    @DisplayName("结构变化抛业务异常")
    void structureChangeThrows() {
        FundCatalogAdapter adapter = new FundCatalogAdapter(new StubFetcher(""), "https://example.com/fund.js");

        assertThatThrownBy(() -> adapter.parse("<html>验证页</html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到数组边界");
        assertThatThrownBy(() -> adapter.parse("var r = [];"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("基金全集为空");
        assertThatThrownBy(() -> adapter.parse("var r = [[\"000001\",\"a\",\"b\"]];"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("列数不是 5");
    }

    /**
     * 固定返回文本的取数桩
     */
    private static class StubFetcher implements TextFetcher {

        private final String text;

        StubFetcher(String text) {
            this.text = text;
        }

        @Override
        public String getText(String url) {
            return text;
        }

        @Override
        public String getText(String url, Charset charset) {
            return text;
        }
    }
}
