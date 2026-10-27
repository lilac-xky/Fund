package com.lilac.adapter.quote;

import com.lilac.domain.dto.quote.QuoteBatchRequest;
import com.lilac.domain.vo.ForexRateVO;
import com.lilac.domain.vo.MarketHistoryVO;
import com.lilac.domain.vo.MarketQuoteVO;
import com.lilac.domain.vo.QuoteBatchVO;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.service.MarketQuoteAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 行情验收测试，外部数据源为真实上游，Redis 走真实实例用于核对缓存
 */
@SpringBootTest
class MarketQuoteIntegrationTest {

    @Autowired
    private MarketQuoteAppService marketQuoteAppService;

    /**
     * 一次批量调用能取回 10 只以上标的的当日涨跌幅
     */
    @Test
    @DisplayName("批量取回 10 只以上标的行情")
    void batchOfMoreThanTenSymbols() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600519", "sz000001", "sh601318", "sh600036", "sz000858",
                "sh601899", "sz002594", "sh600276", "sh601166", "sz000651", "sh600000", "sh601288"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getRequested()).isEqualTo(12);
        assertThat(result.getUsableCount()).isGreaterThanOrEqualTo(10);
        assertThat(result.getQuotes()).hasSize(12);
        assertThat(result.getQuotes()).allSatisfy(quote -> {
            assertThat(quote.getSymbol()).isNotBlank();
            assertThat(quote.getChangePercent()).isNotNull();
            assertThat(quote.getTradeDate()).isNotNull();
        });
    }

    /**
     * realtime 由「数据日期是否就是今天」决定：是今天即 true，数据停在上一交易日即 false
     */
    @Test
    @DisplayName("实时标记与数据日期一致")
    void realtimeMatchesTradeDate() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600519"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        MarketQuoteVO quote = result.getQuotes().get(0);
        assertThat(quote.getTradeDate()).isNotNull();
        assertThat(quote.getRealtime()).isEqualTo(quote.getTradeDate().equals(LocalDate.now()));
    }

    /**
     * 港股能取到价格，汇率可单独取
     */
    @Test
    @DisplayName("港股行情与汇率可分别取到")
    void hkQuoteAndForex() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("hk00700", "hk09988"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getUsableCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getQuotes()).anySatisfy(quote -> {
            assertThat(quote.getSymbol()).startsWith("hk");
            assertThat(quote.getPrice()).isNotNull();
            assertThat(quote.getChangePercent()).isNotNull();
        });

        ForexRateVO forex = marketQuoteAppService.hkdCny();
        assertThat(forex.getPair()).isEqualTo("HKDCNY");
        assertThat(forex.getRate()).isNotNull();
        assertThat(forex.getChangeRate()).isNotNull();
        assertThat(forex.getTradeDate()).isNotNull();
    }

    /**
     * 停牌标的被明确标记，不被当成 0% 计入
     */
    @Test
    @DisplayName("停牌标的标记为不可用")
    void suspendedMarkedUnusable() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600870", "sz000662"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getSuspendedCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getQuotes()).anySatisfy(quote -> {
            assertThat(quote.getStatus()).isEqualTo(com.lilac.adapter.model.QuoteStatus.SUSPENDED);
            assertThat(quote.getUsable()).isFalse();
            assertThat(quote.getUnavailableReason()).contains("停牌");
        });
    }

    /**
     * 不存在的代码返回不可用而不是抛栈，也不影响同批其他标的
     */
    @Test
    @DisplayName("不存在的代码不影响同批其他标的")
    void unknownCodeDoesNotBreakBatch() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("sh600519", "sh999999"));

        QuoteBatchVO result = marketQuoteAppService.batchQuotes(request);

        assertThat(result.getQuotes()).hasSize(2);
        assertThat(result.getQuotes().get(0).getUsable()).isTrue();
        assertThat(result.getQuotes().get(1).getUsable()).isFalse();
        assertThat(result.getQuotes().get(1).getUnavailableReason()).isNotBlank();
    }

    /**
     * 日线取数返回数据；主源可用时为前复权，退到备用源时 adjusted=false 必须显式标出
     */
    @Test
    @DisplayName("日线取到数据并标注复权口径")
    void historyIsAdjusted() {
        List<MarketHistoryVO> histories = marketQuoteAppService.histories(List.of("sh600519", "sz300308"), 20);

        assertThat(histories).hasSize(2);
        assertThat(histories).allSatisfy(history -> {
            assertThat(history.getSize()).isGreaterThanOrEqualTo(15);
            assertThat(history.getBars()).isNotEmpty();
            assertThat(history.getAdjusted()).isNotNull();
            if (Boolean.TRUE.equals(history.getAdjusted())) {
                assertThat(history.getSource()).isEqualTo("tencent");
            } else {
                // 备用源不复权，来源与口径必须能对上，不允许静默给未标注的序列
                assertThat(history.getSource()).isEqualTo("sina");
            }
        });
    }

    /**
     * 非法代码在入参层被拒，不进入取数逻辑
     */
    @Test
    @DisplayName("非法代码抛参数异常")
    void invalidCodeRejected() {
        QuoteBatchRequest request = new QuoteBatchRequest();
        request.setCodes(List.of("abc"));

        assertThatThrownBy(() -> marketQuoteAppService.batchQuotes(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式错误");
        assertThatThrownBy(() -> marketQuoteAppService.batchQuotes(new QuoteBatchRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空")
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode()));
    }
}
