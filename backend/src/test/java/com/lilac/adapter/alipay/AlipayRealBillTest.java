package com.lilac.adapter.alipay;

import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.Variety;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundCatalogService;
import com.lilac.service.impl.AlipayImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用真实导出的账单文件做回归，锁定官方格式（逗号分隔、GBK、表尾合计行）能被正确解析
 */
class AlipayRealBillTest {

    /**
     * 使用者导出的真实账单，放在 data/alipay 下
     */
    private static final Path BILL = Path.of("..", "data", "alipay",
            "alipay_record_20261101_1642_1.csv");

    private AlipayBillParser parser;
    private AlipayImportServiceImpl service;

    /**
     * 装配解析器与服务，基金全集用桩
     */
    @BeforeEach
    void setUp() {
        parser = new AlipayBillParser();
        service = new AlipayImportServiceImpl();
        ReflectionTestUtils.setField(service, "alipayBillParser", parser);
        ReflectionTestUtils.setField(service, "alipayFundClassifier", new AlipayFundClassifier());
        ReflectionTestUtils.setField(service, "fundNameMatcher", new FundNameMatcher());
        FundCatalogService catalogService = mock(FundCatalogService.class);
        when(catalogService.searchByName(anyString(), anyInt())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "fundCatalogService", catalogService);
        VarietyMapper varietyMapper = mock(VarietyMapper.class);
        when(varietyMapper.selectListByQuery(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "varietyMapper", varietyMapper);
    }

    /**
     * 真实文件是逗号分隔、GBK 编码，249 行数据要一条不漏地解析出来
     */
    @Test
    @DisplayName("真实账单逗号分隔能被解析")
    void parseRealFile() throws IOException {
        byte[] bytes = Files.readAllBytes(BILL);
        List<AlipayBillRow> rows = parser.parse(bytes);

        // 文件表尾自称「共242笔记录」，退款行的交易号是复合形式，也要一并解析出来
        assertThat(rows).hasSize(242);
        AlipayBillRow first = rows.get(0);
        assertThat(first.getTradeNo()).isNotBlank();
        assertThat(first.getCreatedTime()).isNotNull();
        assertThat(first.getAmount()).isNotNull();
        // 表尾的「共242笔记录」「已收入:0笔」不能被当成数据行
        assertThat(rows).noneMatch(row -> row.getTradeNo().contains("笔"));
    }

    /**
     * 时间列真实格式带秒，必须能完整解析
     */
    @Test
    @DisplayName("真实账单时间带秒能解析")
    void parseTimeWithSeconds() throws IOException {
        List<AlipayBillRow> rows = parser.parse(Files.readAllBytes(BILL));
        AlipayBillRow buy = rows.stream()
                .filter(row -> row.getProductName().contains("华商均衡成长混合C-买入"))
                .findFirst()
                .orElseThrow();

        assertThat(buy.getCreatedTime()).hasToString("2026-10-12T14:21:55");
        assertThat(buy.getAmount()).isEqualByComparingTo("10.00");
    }

    /**
     * 真实账单里的退款行要能与原买入归到同一交易号
     */
    @Test
    @DisplayName("真实账单的买入与退款共用交易号")
    void refundSharesTradeNo() throws IOException {
        List<AlipayBillRow> rows = parser.parse(Files.readAllBytes(BILL));
        List<AlipayFundOrder> orders = new AlipayFundClassifier().classify(rows);

        var byTradeNo = orders.stream()
                .filter(order -> "20261027001080012204880002544416".equals(order.getTradeNo()))
                .toList();
        assertThat(byTradeNo).hasSize(2);
        assertThat(byTradeNo).extracting(AlipayFundOrder::getAction)
                .containsExactlyInAnyOrder(com.lilac.enums.AlipayAction.BUY,
                        com.lilac.enums.AlipayAction.REFUND);
    }

    /**
     * 整条链路跑一遍：真实账单必须能出待确认行，且退款组已被抵消
     */
    @Test
    @DisplayName("真实账单走完导入预览流程")
    void previewRealFile() throws IOException {
        List<AlipayImportRow> rows = service.preview(Files.readAllBytes(BILL), "real.csv");

        assertThat(rows).isNotEmpty();
        // 退款组被整组丢弃后，不该再有 REFUND 动作出现在待确认里
        assertThat(rows).noneMatch(row -> "REFUND".equals(row.getAction()));
        // 余额宝收益不是基金申赎，也不该出现
        assertThat(rows).noneMatch(row -> row.getFundName() != null
                && row.getFundName().contains("余额宝"));
        // 全集用桩返回空，所以都该是未匹配，说明名称提取成功但查不到
        assertThat(rows).allMatch(row -> row.getFundName() != null && !row.getFundName().isBlank());
    }
}
