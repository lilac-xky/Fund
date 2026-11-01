package com.lilac.adapter.alipay;

import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.Variety;
import com.lilac.mapper.HoldingChangeMapper;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundCatalogService;
import com.lilac.service.impl.AlipayImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
 * 转换找零回归：退款是净值差异的找零，转换必须保留且金额扣掉找零
 */
class AlipayConvertRefundMergeTest {

    private AlipayImportServiceImpl service;

    /**
     * 装配服务，全集与品种表用桩
     */
    @BeforeEach
    void setUp() {
        service = new AlipayImportServiceImpl();
        ReflectionTestUtils.setField(service, "alipayBillParser", new AlipayBillParser());
        ReflectionTestUtils.setField(service, "alipayFundClassifier", new AlipayFundClassifier());
        ReflectionTestUtils.setField(service, "fundNameMatcher", new FundNameMatcher());
        FundCatalogService catalogService = mock(FundCatalogService.class);
        when(catalogService.searchByName(anyString(), anyInt())).thenReturn(List.of());
        when(catalogService.listAllNames()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "fundCatalogService", catalogService);
        VarietyMapper varietyMapper = mock(VarietyMapper.class);
        when(varietyMapper.selectListByQuery(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "varietyMapper", varietyMapper);
        HoldingChangeMapper changeMapper = mock(HoldingChangeMapper.class);
        when(changeMapper.selectListByQuery(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "holdingChangeMapper", changeMapper);
    }

    /**
    /**
     * 账单里转换行的金额已是扣完差异的净额，退款行只走到账侧流水，按组抵消后转换保留原额
     */
    @Test
    @DisplayName("转换保留账单净额，退款行不出现")
    void convertKeepsBillNetAmount() throws Exception {
        List<AlipayImportRow> rows = service.preview(
                Files.readAllBytes(Path.of("..", "data", "alipay",
                        "alipay_record_20261101_1642_1.csv")), "real.csv");

        // 8-10 华商→金信民兴：账单金额 18.80 已是净额，不得再减 0.84
        AlipayImportRow convert = rows.stream()
                .filter(row -> "CONVERT".equals(row.getAction())
                        && "华商均衡成长混合C".equals(row.getFundName())
                        && row.getTradeNo() != null && row.getTradeNo().startsWith("20260810"))
                .findFirst()
                .orElseThrow();
        assertThat(convert.getAmount()).isEqualByComparingTo("18.80");

        // 转入侧与转出侧金额一致
        AlipayImportRow in = rows.stream()
                .filter(row -> "CONVERT_IN".equals(row.getAction())
                        && "金信民兴债券C".equals(row.getFundName())
                        && row.getTradeNo() != null
                        && row.getTradeNo().startsWith("20260810"))
                .findFirst()
                .orElseThrow();
        assertThat(in.getAmount()).isEqualByComparingTo("18.80");

        // 退款行（含转换找零退款）不再单独出现
        assertThat(rows).noneMatch(row -> "REFUND".equals(row.getAction()));
    }

    /**
     * 造一条已注册品种，供其他用例复用
     *
     * @param key  业务主键
     * @param code 代码
     * @param name 名称
     * @return 品种
     */
    private Variety variety(String key, String code, String name) {
        Variety variety = new Variety();
        variety.setVarietyKey(key);
        variety.setVarietyCode(code);
        variety.setVarietyName(name);
        return variety;
    }

    /**
     * 防止未使用告警的占位
     *
     * @return 空
     */
    private FundCatalog unused() {
        return new FundCatalog();
    }
}
