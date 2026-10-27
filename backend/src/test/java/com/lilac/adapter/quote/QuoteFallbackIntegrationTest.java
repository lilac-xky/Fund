package com.lilac.adapter.quote;

import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.vo.MarketQuoteVO;
import com.lilac.domain.vo.QuoteBatchVO;
import com.lilac.service.MarketQuoteAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多源回退验收测试：把主源地址改成不可达地址，验证功能不中断且结果带出来源
 */
@SpringBootTest(properties = {
        "fund.adapter.tencent.kline-url=https://127.0.0.1:1/fqkline?param={symbol},day,,,{limit},qfq",
        "fund.adapter.quote.cache-ttl-seconds=1"
})
class QuoteFallbackIntegrationTest {

    @Autowired
    private MarketQuoteAppService marketQuoteAppService;

    /**
     * 主源日线不可达时仍能拿到日线，且 adjusted=false 与来源能对上
     */
    @Test
    @DisplayName("主源日线不可达时自动切到备用源")
    void historyFallsBackToSina() {
        List<com.lilac.domain.vo.MarketHistoryVO> histories =
                marketQuoteAppService.histories(List.of("sh600519"), 20);

        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getSize()).isGreaterThanOrEqualTo(15);
        assertThat(histories.get(0).getSource()).isEqualTo("sina");
        assertThat(histories.get(0).getAdjusted()).isFalse();
    }

    /**
     * 批量行情在主源日线不可达时不受影响，实时接口是独立地址
     */
    @Test
    @DisplayName("批量行情不受日线源影响")
    void quotesStillWork() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600519", "sz000001", "sh000300"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getUsableCount()).isEqualTo(3);
        assertThat(result.getQuotes()).extracting(MarketQuoteVO::getSymbol)
                .containsExactly("sh600519", "sz000001", "sh000300");
    }

    /**
     * 000300 按号段会被推断成深市，深市无该代码时补查沪市命中上证指数系列
     */
    @Test
    @DisplayName("000300 补查到沪市指数")
    void ambiguousCodeResolved() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("000300", "sh000300"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        MarketQuoteVO inferred = result.getQuotes().get(0);
        MarketQuoteVO explicit = result.getQuotes().get(1);
        assertThat(inferred.getUsable()).isTrue();
        assertThat(inferred.getName()).contains("300");
        assertThat(inferred.getChangePercent()).isEqualByComparingTo(explicit.getChangePercent());
    }
}
