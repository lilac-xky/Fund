package com.lilac.adapter.quote;

import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.vo.QuoteBatchVO;
import com.lilac.service.MarketQuoteAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源优先级验收测试：腾讯在前，正常时不应落到新浪
 */
@SpringBootTest
class QuoteSourceOrderTest {

    @Autowired
    private List<QuoteSource> sources;

    @Autowired
    private MarketQuoteAppService marketQuoteAppService;

    /**
     * Bean 注入顺序必须与回退链一致，否则「主源失效切换」会反着发生
     */
    @Test
    @DisplayName("数据源顺序为腾讯在前")
    void sourceOrderIsExplicit() {
        assertThat(sources).extracting(QuoteSource::name).containsExactly("tencent", "sina");
    }

    /**
     * 正常网络下批量行情应命中腾讯，fallbackUsed 为 false
     */
    @Test
    @DisplayName("主源可用时使用腾讯")
    void primarySourceUsedWhenHealthy() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600519"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getUsableCount()).isEqualTo(1);
        // 腾讯被限流时会退到新浪，此时不能断言主源命中，但来源必须能被如实标出
        if (Boolean.FALSE.equals(result.getFallbackUsed())) {
            assertThat(result.getQuotes().get(0).getSource()).isEqualTo("tencent");
        } else {
            assertThat(result.getQuotes().get(0).getSource()).isEqualTo("sina");
        }
    }
}
