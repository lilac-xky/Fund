package com.lilac.adapter.fund;

import com.lilac.enums.FundCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基金类型推断测试，覆盖六类判定与两种不可自动识别的情形
 */
class FundCategoryResolverTest {

    private final FundCategoryResolver resolver = new FundCategoryResolver();

    /**
     * 债券、混合、股票三类直接取上游类型
     */
    @Test
    @DisplayName("上游三类直接取用")
    void rawThreeCategories() {
        assertThat(resolver.resolve("债券型-长债", "招商产业债券A", false)).isEqualTo(FundCategory.BOND);
        assertThat(resolver.resolve("混合型-灵活", "华夏成长混合", false)).isEqualTo(FundCategory.MIXED);
        assertThat(resolver.resolve("股票型", "易方达消费行业股票", false)).isEqualTo(FundCategory.STOCK);
        assertThat(resolver.resolve("指数型-固收", "国债ETF国泰", true)).isEqualTo(FundCategory.BOND);
    }

    /**
     * 黄金按名称关键词识别，黄金股不能当成黄金
     */
    @Test
    @DisplayName("黄金按名称关键词识别")
    void goldByKeywords() {
        assertThat(resolver.resolve("指数型-其他", "华安黄金ETF联接A", false)).isEqualTo(FundCategory.GOLD);
        assertThat(resolver.resolve("指数型-其他", "黄金ETF华安", true)).isEqualTo(FundCategory.GOLD);
        assertThat(resolver.resolve("指数型-其他", "上海金ETF建信", true)).isEqualTo(FundCategory.GOLD);
        assertThat(resolver.resolve("指数型-股票", "黄金股ETF工银", true)).isEqualTo(FundCategory.STOCK);
        assertThat(resolver.resolve("指数型-股票", "永赢中证沪深港黄金产业股票ETF发起联接A", false))
                .isEqualTo(FundCategory.STOCK);
    }

    /**
     * 红利按名称关键词识别，改革红利类主题基金不属于红利
     */
    @Test
    @DisplayName("红利按名称关键词识别")
    void dividendByKeywords() {
        assertThat(resolver.resolve("指数型-股票", "富国中证红利指数增强A", false)).isEqualTo(FundCategory.DIVIDEND);
        assertThat(resolver.resolve("混合型-灵活", "华夏红利混合", false)).isEqualTo(FundCategory.DIVIDEND);
        assertThat(resolver.resolve("混合型-灵活", "易方达改革红利混合", false)).isEqualTo(FundCategory.MIXED);
        assertThat(resolver.resolve("股票型", "建信改革红利股票A", false)).isEqualTo(FundCategory.STOCK);
    }

    /**
     * 无法识别的类型返回待确认，不猜成普通股票型
     */
    @Test
    @DisplayName("无法识别时返回待确认")
    void unknownWhenUncertain() {
        assertThat(resolver.resolve("", "某只新基金", false)).isEqualTo(FundCategory.UNKNOWN);
        assertThat(resolver.resolve("FOF-稳健型", "某某稳健FOF", false)).isEqualTo(FundCategory.UNKNOWN);
        assertThat(resolver.resolve("QDII-商品", "易方达黄金主题人民币C", false)).isEqualTo(FundCategory.UNKNOWN);
        assertThat(resolver.resolve("指数型-其他", "有色期货ETF联接A", false)).isEqualTo(FundCategory.UNKNOWN);
    }

    /**
     * 场内 ETF 判为 ETF，场外指数基金判为股票型
     */
    @Test
    @DisplayName("指数型按是否场内区分 ETF 与股票型")
    void indexFundByExchange() {
        assertThat(resolver.resolve("指数型-股票", "沪深300ETF华泰柏瑞", true)).isEqualTo(FundCategory.ETF);
        assertThat(resolver.resolve("指数型-股票", "华夏沪深300ETF联接A", false)).isEqualTo(FundCategory.STOCK);
    }
}
