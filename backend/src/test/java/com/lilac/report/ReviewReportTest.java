package com.lilac.report;

import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.ReportRowVO;
import com.lilac.domain.vo.ReviewReportVO;
import com.lilac.enums.FundCategory;
import com.lilac.enums.VarietyType;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.VarietyService;
import com.lilac.service.impl.ReviewReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 复盘报告测试，重点是 CSV 的 Excel 中文兼容、字段转义与误差统计口径
 */
class ReviewReportTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 30);

    @TempDir
    Path tempDir;

    private EstimateErrorService estimateErrorService;
    private VarietyService varietyService;
    private ReviewReportServiceImpl service;

    /**
     * 装配报告服务，数据目录指到临时目录
     */
    @BeforeEach
    void setUp() {
        estimateErrorService = mock(EstimateErrorService.class);
        varietyService = mock(VarietyService.class);
        service = new ReviewReportServiceImpl();
        ReflectionTestUtils.setField(service, "estimateErrorService", estimateErrorService);
        ReflectionTestUtils.setField(service, "varietyService", varietyService);
        ReflectionTestUtils.setField(service, "csvWriter", new CsvWriter());
        ReflectionTestUtils.setField(service, "dataRootResolver",
                new com.lilac.config.DataRootResolver(tempDir.toString()));
    }

    /**
     * CSV 必须带 UTF-8 BOM，否则 Excel 打开中文乱码
     */
    @Test
    @DisplayName("导出的 CSV 带 UTF-8 BOM")
    void csvHasBom() throws IOException {
        stubTwoRows();
        ReviewReportVO report = service.exportComparison(TRADE_DATE);

        Path csv = Path.of(report.getSavedPath());
        assertThat(csv).exists();
        byte[] bytes = Files.readAllBytes(csv);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    /**
     * 去掉 BOM 后中文能正确按 UTF-8 读回，说明编码没写错
     */
    @Test
    @DisplayName("CSV 中文按 UTF-8 可正确读回")
    void csvChineseReadable() throws IOException {
        stubTwoRows();
        ReviewReportVO report = service.exportComparison(TRADE_DATE);

        String content = Files.readString(Path.of(report.getSavedPath()), StandardCharsets.UTF_8);
        assertThat(content).contains("沪深300ETF").contains("易方达中债").contains("交易日");
    }

    /**
     * 含逗号与引号的品种名必须被正确转义，否则列会错位
     */
    @Test
    @DisplayName("含逗号与引号的字段被正确转义")
    void csvEscapesSpecialChars() throws IOException {
        CsvWriter writer = new CsvWriter();
        Path target = tempDir.resolve("escape.csv");
        writer.write(target, List.of("名称", "备注"),
                List.of(List.of("名称含,逗号", "含\"引号\""), List.of("普通", "换行\n内容")));

        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertThat(content).contains("\"名称含,逗号\"");
        assertThat(content).contains("\"含\"\"引号\"\"\"");
    }

    /**
     * 误差统计的中位数与最大值的口径正确
     */
    @Test
    @DisplayName("误差统计口径正确")
    void statsComputed() {
        stubTwoRows();
        ReviewReportVO report = service.generate(TRADE_DATE);

        // 两只误差 0.33 与 0.13，中位数为两者均值 0.23，最大 0.33
        assertThat(report.getMedianAbsError()).isEqualByComparingTo("0.2300");
        assertThat(report.getMaxAbsError()).isEqualByComparingTo("0.3300");
        assertThat(report.getOverThresholdCount()).isEqualTo(1);
        assertThat(report.getNotes()).anyMatch(text -> text.contains("超阈值"));
    }

    /**
     * 当日报告留存在 data/report 下，路径可回查
     */
    @Test
    @DisplayName("当日报告生成并留存")
    void reportSaved() {
        stubTwoRows();
        ReviewReportVO report = service.generate(TRADE_DATE);

        assertThat(report.getSavedPath()).isNotNull();
        assertThat(Path.of(report.getSavedPath())).exists();
        assertThat(report.getSavedPath()).contains("report");
    }

    /**
     * 无误差记录时明确说明原因，不给空表格
     */
    @Test
    @DisplayName("无误差记录时说明原因")
    void emptyExplained() {
        when(estimateErrorService.listEstimateError(any(EstimateErrorQueryRequest.class))).thenReturn(List.of());
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of());

        ReviewReportVO report = service.generate(TRADE_DATE);

        assertThat(report.getRows()).isEmpty();
        assertThat(report.getNotes()).anyMatch(text -> text.contains("没有误差记录"));
    }

    /**
     * 误差历史区间导出返回可读说明与行数
     */
    @Test
    @DisplayName("误差历史可按区间导出")
    void errorHistoryExport() {
        stubTwoRows();

        String message = service.exportErrorHistory(TRADE_DATE.minusDays(7), TRADE_DATE);

        assertThat(message).contains("已导出").contains("2 行");
    }

    /**
     * 造两条误差记录与对应品种
     */
    private void stubTwoRows() {
        EstimateErrorVO first = new EstimateErrorVO();
        first.setVarietyKey("ETF:510300");
        first.setTradeDate(TRADE_DATE);
        first.setEstimatedChange(new BigDecimal("-1.03"));
        first.setActualChange(new BigDecimal("-1.36"));
        first.setErrorValue(new BigDecimal("0.33"));
        first.setThresholdValue(new BigDecimal("0.30"));
        first.setOverThreshold(Boolean.TRUE);
        first.setDirectionHit(Boolean.TRUE);
        first.setOutputTier("A");

        EstimateErrorVO second = new EstimateErrorVO();
        second.setVarietyKey("OF:006327");
        second.setTradeDate(TRADE_DATE);
        second.setEstimatedChange(new BigDecimal("-0.0412"));
        second.setActualChange(new BigDecimal("-0.16"));
        second.setErrorValue(new BigDecimal("0.13"));
        second.setThresholdValue(new BigDecimal("0.10"));
        second.setOverThreshold(Boolean.FALSE);
        second.setDirectionHit(Boolean.TRUE);
        second.setOutputTier("A");

        when(estimateErrorService.listEstimateError(any(EstimateErrorQueryRequest.class)))
                .thenReturn(List.of(first, second));
        when(varietyService.listVariety(any(VarietyQueryRequest.class)))
                .thenReturn(List.of(variety("ETF:510300", "510300", "沪深300ETF", FundCategory.ETF),
                        variety("OF:006327", "006327", "易方达中债", FundCategory.BOND)));
    }

    /**
     * 造一只品种
     *
     * @param key      业务主键
     * @param code     6 位代码
     * @param name     名称
     * @param category 类型
     * @return 品种
     */
    private VarietyVO variety(String key, String code, String name, FundCategory category) {
        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey(key);
        variety.setVarietyCode(code);
        variety.setVarietyName(name);
        variety.setCategory(category);
        variety.setVarietyType(VarietyType.ETF);
        return variety;
    }
}
