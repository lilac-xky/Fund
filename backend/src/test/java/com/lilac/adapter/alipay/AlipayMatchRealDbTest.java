package com.lilac.adapter.alipay;

import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundCatalogService;
import com.lilac.service.impl.AlipayImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 连真实库跑一遍：账单里的基金名要能与已注册品种对上，而不是一律「未匹配」
 *
 * <p>依赖 dev 库里的真实持仓，故只在 dev 配置下执行：空库或换机器时自动跳过，不打扰常规回归。</p>
 */
@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "fund.realDbTest", matches = "true")
class AlipayMatchRealDbTest {

    @Autowired
    private VarietyMapper varietyMapper;

    @Autowired
    private FundCatalogService fundCatalogService;

    @Autowired
    private AlipayBillParser parser;

    @Autowired
    private AlipayFundClassifier classifier;

    @Autowired
    private FundNameMatcher fundNameMatcher;

    /**
     * 真实账单里出现过的基金名，凡是持仓里有的都必须能匹配上
     */
    @Test
    void registeredFundsAreMatched() throws Exception {
        AlipayImportServiceImpl service = new AlipayImportServiceImpl();
        ReflectionTestUtils.setField(service, "alipayBillParser", parser);
        ReflectionTestUtils.setField(service, "alipayFundClassifier", classifier);
        ReflectionTestUtils.setField(service, "fundNameMatcher", fundNameMatcher);
        ReflectionTestUtils.setField(service, "fundCatalogService", fundCatalogService);
        ReflectionTestUtils.setField(service, "varietyMapper", varietyMapper);

        List<AlipayImportRow> rows = service.preview(
                Files.readAllBytes(Path.of("..", "data", "alipay",
                        "alipay_record_20261101_1642_1.csv")), "real.csv");

        Map<String, Long> byStatus = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getStatus(), Collectors.counting()));
        System.out.println("\\n=== 真实库匹配后状态分布 ===");
        byStatus.forEach((key, value) -> System.out.println("  " + key + " : " + value));

        System.out.println("\\n=== 匹配到已注册品种的行 ===");
        rows.stream()
                .filter(row -> row.getVarietyKey() != null)
                .collect(Collectors.groupingBy(
                        row -> row.getFundName() + " -> " + row.getVarietyKey()
                                + (row.getCandidates().isEmpty() ? ""
                                : " (相似度 " + row.getCandidates().get(0).getSimilarity() + ")"),
                        Collectors.counting()))
                .forEach((key, value) -> System.out.println("  " + value + "  " + key));

        // 使用者持仓里的这几只必须能对上，不能是「未匹配」
        assertMatched(rows, "中信保诚至泰中短债债券C");
        assertMatched(rows, "南方中证银行ETF联接C");
        assertMatched(rows, "华泰柏瑞中证红利低波动ETF联接C");
        assertMatched(rows, "摩根标普500指数(QDII)A");
        assertMatched(rows, "国泰黄金ETF联接C");
        assertMatched(rows, "华商均衡成长混合C");

        System.out.println("\n=== 仍未匹配的基金名 ===");
        rows.stream().filter(row -> row.getVarietyKey() == null && !row.getCandidates().isEmpty())
                .collect(Collectors.groupingBy(row -> row.getFundName(),
                        Collectors.counting()))
                .forEach((key, value) -> System.out.println("  待选 " + value + "  " + key));
        rows.stream().filter(row -> row.getCandidates().isEmpty())
                .collect(Collectors.groupingBy(row -> row.getFundName(),
                        Collectors.counting()))
                .forEach((key, value) -> System.out.println("  查无 " + value + "  " + key));
    }

    /**
     * 断言某只基金的行至少有一条匹配到了品种主键
     *
     * @param rows     待确认行
     * @param fundName 账单里的基金名
     */
    private void assertMatched(List<AlipayImportRow> rows, String fundName) {
        boolean matched = rows.stream()
                .filter(row -> fundName.equals(row.getFundName()))
                .anyMatch(row -> row.getVarietyKey() != null);
        org.assertj.core.api.Assertions.assertThat(matched)
                .as("「%s」应能匹配到已注册品种", fundName)
                .isTrue();
    }
}
