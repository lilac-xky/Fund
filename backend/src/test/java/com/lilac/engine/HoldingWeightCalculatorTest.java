package com.lilac.engine;

import com.lilac.adapter.model.ForexRate;
import com.lilac.adapter.model.MarketQuote;
import com.lilac.adapter.model.MarketSymbol;
import com.lilac.adapter.model.QuoteStatus;
import com.lilac.domain.vo.HoldingItemVO;
import com.lilac.engine.model.WeightedOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持仓加权测试，核心是「残差按 0 计」「停牌标的剔除」「港股乘汇率」
 */
class HoldingWeightCalculatorTest {

    private HoldingWeightCalculator calculator;

    /**
     * 装配计算器
     */
    @BeforeEach
    void setUp() {
        calculator = new HoldingWeightCalculator();
    }

    /**
     * 两只标的各占一半，残差按 0 计后结果就是两行的加权和
     */
    @Test
    @DisplayName("残差按 0 计，不按指数补齐")
    void residualCountsAsZero() {
        List<HoldingItemVO> holdings = List.of(
                new HoldingItemVO("600519", "贵州茅台", new BigDecimal("30")),
                new HoldingItemVO("000001", "平安银行", new BigDecimal("20")));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "2.00"));
        quotes.put("sz000001", okQuote("sz000001", "-1.00"));

        WeightedOutcome outcome = calculator.weighted(holdings, quotes, null);

        // 30% × 2.00 + 20% × (−1.00) = 0.40 个百分点
        assertThat(outcome.getChange()).isEqualByComparingTo("0.400000");
        assertThat(outcome.getUsedWeight()).isEqualByComparingTo("50");
        assertThat(outcome.getDisclosedWeight()).isEqualByComparingTo("50");
        assertThat(outcome.getExcludedCount()).isZero();
    }

    /**
     * 停牌标的被剔除且不影响其他标的，同时记录停牌名单
     */
    @Test
    @DisplayName("停牌标的剔除并记录名单")
    void suspendedExcluded() {
        List<HoldingItemVO> holdings = List.of(
                new HoldingItemVO("600519", "贵州茅台", new BigDecimal("40")),
                new HoldingItemVO("600870", "退市海润", new BigDecimal("10")));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("sh600519", okQuote("sh600519", "1.50"));
        MarketQuote suspended = okQuote("sh600870", "0.00");
        suspended.setStatus(QuoteStatus.SUSPENDED);
        suspended.setChangePercent(null);
        quotes.put("sh600870", suspended);

        WeightedOutcome outcome = calculator.weighted(holdings, quotes, null);

        assertThat(outcome.getUsedWeight()).isEqualByComparingTo("40");
        assertThat(outcome.getChange()).isEqualByComparingTo("0.600000");
        assertThat(outcome.getExcludedCount()).isEqualTo(1);
        assertThat(outcome.getSuspendedSymbols()).contains("600870");
    }

    /**
     * 港股收益按 (1 + r_hk)(1 + r_fx) − 1 折算，A 股不受汇率影响
     */
    @Test
    @DisplayName("港股按汇率复利折算")
    void hkConvertedByForex() {
        List<HoldingItemVO> holdings = List.of(
                new HoldingItemVO("00700", "腾讯控股", new BigDecimal("50")),
                new HoldingItemVO("600519", "贵州茅台", new BigDecimal("50")));
        Map<String, MarketQuote> quotes = new LinkedHashMap<>();
        quotes.put("hk00700", okQuote("hk00700", "2.00"));
        quotes.put("sh600519", okQuote("sh600519", "1.00"));

        WeightedOutcome outcome = calculator.weighted(holdings, quotes, forexOf("0.005"));

        // 港股 2.00% 折算成 (1.02 × 1.005 − 1) = 2.51%，A 股 1.00%
        assertThat(calculator.cnyChange(quotes.get("hk00700"), forexOf("0.005")))
                .isEqualByComparingTo("2.5100");
        assertThat(outcome.getChange()).isEqualByComparingTo("1.755000");
    }

    /**
     * 全部标的都取不到行情时返回空结果，由引擎判为不可预估
     */
    @Test
    @DisplayName("全部无行情时返回空结果")
    void emptyWhenNoQuotes() {
        List<HoldingItemVO> holdings = List.of(new HoldingItemVO("600519", "贵州茅台", new BigDecimal("40")));

        WeightedOutcome outcome = calculator.weighted(holdings, Map.of(), null);

        assertThat(outcome.empty()).isTrue();
        assertThat(outcome.getChange()).isNull();
    }

    /**
     * 无前缀代码按号段推断市场，港股 5 位代码推成 hk 前缀
     */
    @Test
    @DisplayName("代码按号段推断市场前缀")
    void inferSymbolPrefix() {
        assertThat(HoldingWeightCalculator.symbolOf("600519")).isEqualTo("sh600519");
        assertThat(HoldingWeightCalculator.symbolOf("000001")).isEqualTo("sz000001");
        assertThat(HoldingWeightCalculator.symbolOf("00700")).isEqualTo("hk00700");
        assertThat(HoldingWeightCalculator.symbolOf("920599")).isEqualTo("bj920599");
    }

    /**
     * 构造一条可用行情
     *
     * @param symbol  带前缀代码
     * @param percent 涨跌幅
     * @return 行情
     */
    private MarketQuote okQuote(String symbol, String percent) {
        MarketQuote quote = new MarketQuote();
        quote.setSymbol(MarketSymbol.of(symbol));
        quote.setChangePercent(new BigDecimal(percent));
        quote.setPrice(new BigDecimal("10.00"));
        quote.setPreviousClose(new BigDecimal("9.90"));
        quote.setTradeDate(LocalDate.now());
        quote.setStatus(QuoteStatus.OK);
        quote.setSource("stub");
        quote.setAdjusted(Boolean.TRUE);
        quote.setRealtime(Boolean.TRUE);
        return quote;
    }

    /**
     * 构造一份可用汇率
     *
     * @param changeRate 汇率变动
     * @return 汇率
     */
    private ForexRate forexOf(String changeRate) {
        ForexRate rate = new ForexRate();
        rate.setPair("HKDCNY");
        rate.setRate(new BigDecimal("0.8500000000"));
        rate.setPreviousRate(new BigDecimal("0.8450000000"));
        rate.setChangeRate(new BigDecimal(changeRate));
        rate.setTradeDate(LocalDate.now());
        rate.setSource("sina");
        return rate;
    }
}
