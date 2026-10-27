package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundAssetAllocationPoint;
import com.lilac.adapter.model.FundNetWorthPoint;
import com.lilac.adapter.model.FundPingzhongData;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * pingzhongdata 解析测试，用真实返回片段验证净值与仓位提取
 */
class FundPingzhongDataAdapterTest {

    private static final String SAMPLE = "/*测试数据*/var fS_name = \"大成中证红利指数A\";var fS_code = \"090010\";"
            + "/*股票仓位走势*/var Data_fundSharesPositions = [[1792598400000,81.6700]];"
            + "var Data_netWorthTrend = [{\"x\":1265040000000,\"y\":1.0,\"equityReturn\":0,\"unitMoney\":\"\"},"
            + "{\"x\":1792598400000,\"y\":1.1329,\"equityReturn\":-0.02,\"unitMoney\":\"\"},"
            + "{\"x\":1792684800000,\"y\":1.1328,\"equityReturn\":-0.01,\"unitMoney\":\"\"}];"
            + "var Data_assetAllocation = {\"series\":[{\"name\":\"股票占净比\",\"data\":[94.49,94.33]},"
            + "{\"name\":\"债券占净比\",\"data\":[0,0]},{\"name\":\"现金占净比\",\"data\":[5.63,5.79]},"
            + "{\"name\":\"净资产\",\"data\":[26.5,23.6]}],\"categories\":[\"2026-03-31\",\"2026-06-30\"]};";

    private static final String MONEY_FUND = "var fS_name = \"天弘余额宝货币\";"
            + "var Data_netWorthTrend = [{\"x\":1792684800000,\"y\":0.7,\"equityReturn\":0.01,\"unitMoney\":\"\"}];"
            + "var Data_assetAllocation = {\"series\":[{\"name\":\"期间申购\",\"data\":[12392.31]}],"
            + "\"categories\":[\"2026-09-30\"]};";

    /**
     * 净值序列按日期升序，仓位数与报告期一一对应
     */
    @Test
    @DisplayName("解析净值序列与季末仓位")
    void parseRealSample() {
        FundPingzhongDataAdapter adapter = new FundPingzhongDataAdapter(new StubFetcher(SAMPLE), "https://example.com");

        FundPingzhongData data = adapter.parse("090010", SAMPLE);

        assertThat(data.getName()).isEqualTo("大成中证红利指数A");
        assertThat(data.getNetWorthPoints()).hasSize(3);
        assertThat(data.getNetWorthPoints().get(0).getNetWorthDate()).isEqualTo(LocalDate.of(2010, 2, 2));
        assertThat(data.getNetWorthPoints().get(2).getEquityReturn()).isEqualByComparingTo("-0.01");
        assertThat(data.latestNetWorthDate()).isEqualTo(LocalDate.of(2026, 10, 23));

        List<FundAssetAllocationPoint> allocations = data.getAssetAllocations();
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(1).getReportDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(allocations.get(1).getStockRatio()).isEqualByComparingTo("94.33");
        assertThat(allocations.get(1).getCashRatio()).isEqualByComparingTo("5.79");
        assertThat(data.latestAssetAllocation().getStockRatio()).isEqualByComparingTo("94.33");
    }

    /**
     * 货币基金没有股票仓位，仓位列表为空而不是抛异常
     */
    @Test
    @DisplayName("货币基金无仓位时返回空列表")
    void moneyFundHasNoAllocation() {
        FundPingzhongDataAdapter adapter = new FundPingzhongDataAdapter(new StubFetcher(MONEY_FUND), "https://example.com");

        FundPingzhongData data = adapter.parse("000198", MONEY_FUND);

        assertThat(data.getAssetAllocations()).isEmpty();
        assertThat(data.latestAssetAllocation()).isNull();
        assertThat(data.getNetWorthPoints()).hasSize(1);
    }

    /**
     * 返回 HTML、缺变量、净值数组为空时抛具名异常
     */
    @Test
    @DisplayName("结构变化抛具名异常")
    void structureChangeThrows() {
        FundPingzhongDataAdapter htmlAdapter = new FundPingzhongDataAdapter(
                new StubFetcher("<!doctype html><html><title>页面未找到</title></html>"), "https://example.com");
        FundPingzhongDataAdapter adapter = new FundPingzhongDataAdapter(new StubFetcher(""), "https://example.com");

        assertThatThrownBy(() -> htmlAdapter.fetch("999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回页面而非 JS");
        assertThatThrownBy(() -> adapter.parse("090010", "var fS_name = \"x\";"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无净值序列");
        assertThatThrownBy(() -> adapter.parse("090010", "var Data_netWorthTrend = [];"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("净值序列为空");
        assertThatThrownBy(() -> adapter.parse("090010", "var Data_netWorthTrend = [{\"x\":1}];"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("净值行缺字段");
    }

    /**
     * 固定返回文本的取数桩
     */
    private record StubFetcher(String text) implements TextFetcher {

        /**
         * 返回固定文本
         *
         * @param url 请求地址
         * @return 固定文本
         */
        @Override
        public String getText(String url) {
            return text;
        }

        /**
         * 返回固定文本
         *
         * @param url     请求地址
         * @param charset 响应字符集
         * @return 固定文本
         */
        @Override
        public String getText(String url, Charset charset) {
            return text;
        }
    }
}
