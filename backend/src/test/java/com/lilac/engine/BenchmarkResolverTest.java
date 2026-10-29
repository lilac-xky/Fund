package com.lilac.engine;

import com.lilac.adapter.fund.IndexEtfRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代理标的解析测试，覆盖字母开头的指数代码改取跟踪 ETF 与场内代码原样保留
 */
class BenchmarkResolverTest {

    /**
     * 字母开头的指数代码改取跟踪它的 ETF，否则该基金会因取不到行情被判不可预估
     */
    @Test
    @DisplayName("字母开头的指数代码改取跟踪 ETF")
    void letterIndexUsesTrackingEtf() {
        assertThat(BenchmarkResolver.symbolOf("H30269")).isEqualTo("sh512890");
    }

    /**
     * 数字指数代码原样按号段推断市场，不被代理表改写
     */
    @Test
    @DisplayName("数字指数代码原样推断市场")
    void numericIndexKeepsInferredMarket() {
        assertThat(BenchmarkResolver.symbolOf("000922")).isEqualTo("sz000922");
    }

    /**
     * 未收录的字母指数返回 null，由上层说出不可预估而不是编造一个代码
     */
    @Test
    @DisplayName("未收录的字母指数返回 null")
    void unknownLetterIndexReturnsNull() {
        assertThat(BenchmarkResolver.symbolOf("H99999")).isNull();
    }

    /**
     * 场内 ETF 代码原样转换，不经过指代表
     */
    @Test
    @DisplayName("场内 ETF 代码原样转换")
    void etfCodeConvertsDirectly() {
        assertThat(BenchmarkResolver.symbolOf("512890")).isEqualTo("sh512890");
    }

    /**
     * 代理表收录的字母指数能解析出行情代码，大小写不敏感
     */
    @Test
    @DisplayName("代理表内字母指数可解析")
    void registryCodesResolvable() {
        assertThat(IndexEtfRegistry.findByIndexCode("H30269")).isEqualTo("512890");
        assertThat(BenchmarkResolver.symbolOf("H30269")).isEqualTo("sh512890");
        assertThat(BenchmarkResolver.symbolOf("h30269")).isEqualTo("sh512890");
        // 纯数字指数不进代理表，仍按号段推断，交给行情适配器的跨市场补查
        assertThat(IndexEtfRegistry.findByIndexCode("930955")).isNull();
    }
}
