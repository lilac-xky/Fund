package com.lilac.report;

import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.report.OperationLogQueryRequest;
import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.OperationLogVO;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.HoldingChangeType;
import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import com.lilac.service.HoldingService;
import com.lilac.service.SendRecordService;
import com.lilac.service.VarietyService;
import com.lilac.service.impl.OperationLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 操作日志测试，重点是两个来源合并成一条时间线、CSV 可读且中文不乱码
 */
class OperationLogTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    @TempDir
    Path tempDir;

    private SendRecordService sendRecordService;
    private HoldingService holdingService;
    private VarietyService varietyService;
    private OperationLogServiceImpl service;

    /**
     * 装配服务，数据目录指到临时目录
     */
    @BeforeEach
    void setUp() {
        sendRecordService = mock(SendRecordService.class);
        holdingService = mock(HoldingService.class);
        varietyService = mock(VarietyService.class);
        service = new OperationLogServiceImpl();
        ReflectionTestUtils.setField(service, "sendRecordService", sendRecordService);
        ReflectionTestUtils.setField(service, "holdingService", holdingService);
        ReflectionTestUtils.setField(service, "varietyService", varietyService);
        ReflectionTestUtils.setField(service, "csvWriter", new CsvWriter());
        ReflectionTestUtils.setField(service, "dataRoot", tempDir.toString());
        stub();
    }

    /**
     * 提醒与买卖要合并成一条时间线，按发生时间倒序
     */
    @Test
    @DisplayName("提醒与买卖合并为一条时间线")
    void mergesTwoSources() {
        List<OperationLogVO> logs = service.list(new OperationLogQueryRequest());

        assertThat(logs).hasSize(4);
        // 20:00 晚间提醒最新，09:30 早盘最早，买卖在中间
        assertThat(logs.get(0).getSource()).isEqualTo("SEND");
        assertThat(logs.get(0).getEventType()).contains("晚间");
        assertThat(logs).anyMatch(log -> "买入".equals(log.getEventType()));
        assertThat(logs).anyMatch(log -> "卖出".equals(log.getEventType()));
        // 时间必须严格递减
        for (int i = 1; i < logs.size(); i++) {
            assertThat(logs.get(i - 1).getHappenedAt()).isAfterOrEqualTo(logs.get(i).getHappenedAt());
        }
    }

    /**
     * 买卖日志要带出代码与名称，否则导出后无法核对
     */
    @Test
    @DisplayName("买卖日志带出代码与名称")
    void changeRowHasIdentity() {
        List<OperationLogVO> logs = service.list(new OperationLogQueryRequest());

        OperationLogVO buy = logs.stream()
                .filter(log -> "买入".equals(log.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(buy.getVarietyCode()).isEqualTo("510300");
        assertThat(buy.getVarietyName()).isEqualTo("沪深300ETF");
        assertThat(buy.getAmount()).isEqualByComparingTo("1233.30");
    }

    /**
     * 可按来源过滤，只要买卖时不出现提醒
     */
    @Test
    @DisplayName("可按来源过滤")
    void filtersBySource() {
        OperationLogQueryRequest query = new OperationLogQueryRequest();
        query.setSource("CHANGE");

        List<OperationLogVO> logs = service.list(query);

        assertThat(logs).isNotEmpty();
        assertThat(logs).allMatch(log -> "CHANGE".equals(log.getSource()));
    }

    /**
     * 导出的 CSV 要带 BOM 且中文可读，满足「Excel 中可读」
     */
    @Test
    @DisplayName("导出 CSV 带 BOM 且中文可读")
    void exportCsvReadable() throws Exception {
        String message = service.exportCsv(new OperationLogQueryRequest());

        assertThat(message).contains("已导出").contains("4 行");
        // 路径从返回说明里取，避免依赖「今天」是哪一天
        Path csv = Path.of(message.substring(message.indexOf(tempDir.toString())));
        assertThat(csv).exists();
        byte[] bytes = Files.readAllBytes(csv);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertThat(content).contains("发生时间").contains("沪深300ETF").contains("买入");
    }

    /**
     * 没有记录时导出 0 行，不报错也不编数据
     */
    @Test
    @DisplayName("无记录时导出零行")
    void exportEmpty() {
        when(sendRecordService.listSendRecord(any(SendRecordQueryRequest.class))).thenReturn(List.of());
        when(holdingService.listChanges(any(HoldingChangeQueryRequest.class))).thenReturn(List.of());

        String message = service.exportCsv(new OperationLogQueryRequest());

        assertThat(message).contains("0 行");
    }

    /**
     * 造两条提醒与两条买卖
     */
    private void stub() {
        SendRecordVO morning = new SendRecordVO();
        morning.setSendDate(DAY);
        morning.setSendPoint(SendPoint.MORNING);
        morning.setStatus(SendStatus.SENT);
        morning.setSentTime(DAY.atTime(9, 30));

        SendRecordVO evening = new SendRecordVO();
        evening.setSendDate(DAY);
        evening.setSendPoint(SendPoint.EVENING);
        evening.setStatus(SendStatus.FAILED);
        evening.setSentTime(DAY.atTime(20, 0));
        evening.setFailReason("SMTP 超时");

        when(sendRecordService.listSendRecord(any(SendRecordQueryRequest.class)))
                .thenReturn(List.of(morning, evening));

        HoldingChangeVO buy = new HoldingChangeVO();
        buy.setVarietyKey("ETF:510300");
        buy.setChangeType(HoldingChangeType.BUY);
        buy.setShares(new BigDecimal("300"));
        buy.setUnitPrice(new BigDecimal("4.111"));
        buy.setAmount(new BigDecimal("1233.30"));
        buy.setOpenDate(DAY);
        buy.setCreateTime(DAY.atTime(14, 35));
        buy.setNote("建仓");

        HoldingChangeVO sell = new HoldingChangeVO();
        sell.setVarietyKey("ETF:510300");
        sell.setChangeType(HoldingChangeType.SELL);
        sell.setShares(new BigDecimal("100"));
        sell.setUnitPrice(new BigDecimal("4.2"));
        sell.setAmount(new BigDecimal("420"));
        sell.setOpenDate(DAY);
        sell.setCreateTime(DAY.atTime(15, 5));

        when(holdingService.listChanges(any(HoldingChangeQueryRequest.class))).thenReturn(List.of(buy, sell));

        VarietyVO variety = new VarietyVO();
        variety.setVarietyKey("ETF:510300");
        variety.setVarietyCode("510300");
        variety.setVarietyName("沪深300ETF");
        when(varietyService.listVariety(any(VarietyQueryRequest.class))).thenReturn(List.of(variety));
    }
}
