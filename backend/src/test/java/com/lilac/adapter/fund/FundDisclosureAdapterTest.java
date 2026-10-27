package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundReportDisclosure;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 披露日解析测试，覆盖标题年份写法差异与报告期归属
 */
class FundDisclosureAdapterTest {

    private static final String SAMPLE = "{\"Data\":["
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2026年第3季度报告\",\"PUBLISHDATEDesc\":\"2026-10-27\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2026年中期报告\",\"PUBLISHDATEDesc\":\"2026-08-28\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2026年第2季度报告\",\"PUBLISHDATEDesc\":\"2026-07-21\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2026年第1季度报告\",\"PUBLISHDATEDesc\":\"2026-04-22\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2025年年度报告\",\"PUBLISHDATEDesc\":\"2026-03-31\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2025年第4季度报告\",\"PUBLISHDATEDesc\":\"2026-01-22\"},"
            + "{\"TITLE\":\"大成中证红利指数证券投资基金2025年年度报告摘要\",\"PUBLISHDATEDesc\":\"2026-03-30\"},"
            + "{\"TITLE\":\"关于暂停大额申购的公告\",\"PUBLISHDATEDesc\":\"2026-05-01\"}"
            + "],\"ErrCode\":0}";

    private static final String EMPTY = "{\"Data\":\"\",\"ErrCode\":-999,\"ErrMsg\":\"\"}";

    /**
     * 年报与四季报归到上一年，摘要与非定期报告被剔除
     */
    @Test
    @DisplayName("按发布日期推算报告期并剔除摘要")
    void parseRegularReports() {
        FundDisclosureAdapter adapter = newAdapter(SAMPLE);

        List<FundReportDisclosure> reports = adapter.parse("090010", SAMPLE);

        assertThat(reports).extracting(FundReportDisclosure::getPeriod)
                .containsExactly("2025Q4", "2026Q1", "2026Q2", "2026Q3");
        assertThat(reports).extracting(FundReportDisclosure::getPublishDate).containsExactly(
                LocalDate.of(2026, 1, 22), LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 7, 21), LocalDate.of(2026, 10, 27));
        FundReportDisclosure midYear = reports.stream()
                .filter(report -> "2026Q2".equals(report.getPeriod())).findFirst().orElseThrow();
        // 同一报告期先发季报、后发中报，对外暴露的是快照首次可用的最早披露日
        assertThat(midYear.getPublishDate()).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    /**
     * 数据为空表示该基金没有定期报告，返回空列表
     */
    @Test
    @DisplayName("公告为空返回空列表")
    void emptyDataReturnsEmptyList() {
        FundDisclosureAdapter adapter = newAdapter(EMPTY);

        assertThat(adapter.parse("019022", EMPTY)).isEmpty();
    }

    /**
     * 返回 HTML、缺 Data 字段时抛具名异常
     */
    @Test
    @DisplayName("结构变化抛具名异常")
    void structureChangeThrows() {
        FundDisclosureAdapter adapter = newAdapter("<html>验证页</html>");

        assertThatThrownBy(() -> adapter.parse("090010", "<html>验证页</html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回非 JSON");
        assertThatThrownBy(() -> adapter.parse("090010", "{\"ErrCode\":0}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 Data 字段");
    }

    /**
     * 构造使用固定文本的适配器
     *
     * @param text 固定返回文本
     * @return 适配器
     */
    private FundDisclosureAdapter newAdapter(String text) {
        return new FundDisclosureAdapter(new StubFetcher(text), "https://example.com/{code}",
                "https://fundf10.eastmoney.com/");
    }

    /**
     * 固定返回文本的取数桩
     */
    private record StubFetcher(String text) implements TextFetcher {

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
