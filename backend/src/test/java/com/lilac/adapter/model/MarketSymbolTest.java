package com.lilac.adapter.model;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 行情标的解析测试，号段决定交易所前缀，带前缀时以前缀为准
 */
class MarketSymbolTest {

    /**
     * 6 位代码按号段推断沪、深、北交所
     */
    @Test
    @DisplayName("按号段推断市场")
    void inferBySegment() {
        assertThat(MarketSymbol.of("600519").symbol()).isEqualTo("sh600519");
        assertThat(MarketSymbol.of("510300").symbol()).isEqualTo("sh510300");
        assertThat(MarketSymbol.of("000001").symbol()).isEqualTo("sz000001");
        assertThat(MarketSymbol.of("300308").symbol()).isEqualTo("sz300308");
        assertThat(MarketSymbol.of("920599").symbol()).isEqualTo("bj920599");
        assertThat(MarketSymbol.of("830799").symbol()).isEqualTo("bj830799");
    }

    /**
     * 带前缀时不被号段覆盖，000300 必须能指定成沪市指数
     */
    @Test
    @DisplayName("带前缀时以前缀为准")
    void prefixWins() {
        assertThat(MarketSymbol.of("sh000300").symbol()).isEqualTo("sh000300");
        assertThat(MarketSymbol.of("sz000300").symbol()).isEqualTo("sz000300");
        assertThat(MarketSymbol.of("hk00700").symbol()).isEqualTo("hk00700");
        assertThat(MarketSymbol.of("sh000300").hongKong()).isFalse();
        assertThat(MarketSymbol.of("hk00700").hongKong()).isTrue();
    }

    /**
     * 5 位代码按港股处理
     */
    @Test
    @DisplayName("5 位代码按港股处理")
    void fiveDigitsIsHongKong() {
        assertThat(MarketSymbol.of("00700").symbol()).isEqualTo("hk00700");
        assertThat(MarketSymbol.of("00700").hongKong()).isTrue();
    }

    /**
     * 000 段代码可能在沪深两个市场，需要补查
     */
    @Test
    @DisplayName("000 段代码可识别为需要补查")
    void ambiguousCodeNeedsSibling() {
        MarketSymbol inferred = MarketSymbol.of("000300");

        assertThat(inferred.ambiguous()).isTrue();
        assertThat(inferred.sibling().symbol()).isEqualTo("sh000300");
        assertThat(MarketSymbol.of("sh000300").sibling().symbol()).isEqualTo("sz000300");
        assertThat(MarketSymbol.of("600519").ambiguous()).isFalse();
        assertThat(MarketSymbol.of("hk00700").sibling()).isNull();
    }

    /**
     * 非法代码抛参数异常，不进入取数逻辑
     */
    @Test
    @DisplayName("非法代码抛参数异常")
    void invalidCodeThrows() {
        assertThatThrownBy(() -> MarketSymbol.of("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式错误")
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode()));
        assertThatThrownBy(() -> MarketSymbol.of("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }
}
