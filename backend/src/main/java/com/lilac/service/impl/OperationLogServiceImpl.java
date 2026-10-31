package com.lilac.service.impl;

import com.lilac.domain.dto.holding.HoldingChangeQueryRequest;
import com.lilac.domain.dto.report.OperationLogQueryRequest;
import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.HoldingChangeVO;
import com.lilac.domain.vo.OperationLogVO;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.enums.HoldingChangeType;
import com.lilac.report.CsvWriter;
import com.lilac.service.HoldingService;
import com.lilac.service.OperationLogService;
import com.lilac.service.SendRecordService;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志业务实现，两个来源各自查询后合并排序，不新建表以避免数据重复
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final int DEFAULT_DAYS = 30;
    private static final String SOURCE_SEND = "SEND";
    private static final String SOURCE_CHANGE = "CHANGE";

    @Value("${fund.data.root:data}")
    private String dataRoot;

    @Resource
    private SendRecordService sendRecordService;

    @Resource
    private HoldingService holdingService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private CsvWriter csvWriter;

    /**
     * 合并两个来源为统一时间线
     *
     * @param request 查询请求
     * @return 按发生时间倒序的日志
     */
    @Override
    public List<OperationLogVO> list(OperationLogQueryRequest request) {
        OperationLogQueryRequest query = request == null ? new OperationLogQueryRequest() : request;
        LocalDate end = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        LocalDate start = query.getStartDate() == null ? end.minusDays(DEFAULT_DAYS) : query.getStartDate();
        Map<String, VarietyVO> varietyMap = varietyMap();
        List<OperationLogVO> logs = new ArrayList<>();
        if (wanted(query, SOURCE_SEND)) {
            logs.addAll(sendsOf(start, end, varietyMap));
        }
        if (wanted(query, SOURCE_CHANGE)) {
            logs.addAll(changesOf(start, end, query.getVarietyKey(), varietyMap));
        }
        // 同一时刻的多条记录按来源稳定排序，避免每次刷新顺序都变
        logs.sort(Comparator.comparing(OperationLogVO::getHappenedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OperationLogVO::getSource)
                .reversed());
        return logs;
    }

    /**
     * 导出为 CSV
     *
     * @param request 查询请求
     * @return 导出说明
     */
    @Override
    public String exportCsv(OperationLogQueryRequest request) {
        OperationLogQueryRequest query = request == null ? new OperationLogQueryRequest() : request;
        List<OperationLogVO> logs = list(query);
        LocalDate end = query.getEndDate() == null ? LocalDate.now() : query.getEndDate();
        LocalDate start = query.getStartDate() == null ? end.minusDays(DEFAULT_DAYS) : query.getStartDate();
        Path target = Paths.get(dataRoot).toAbsolutePath().normalize()
                .resolve("op-log").resolve("op-log-" + start + "_" + end + ".csv");
        try {
            int written = csvWriter.write(target, header(), rows(logs));
            return "已导出 " + written + " 行到 " + target;
        } catch (IOException e) {
            log.warn("操作日志导出失败 {}~{}：{}", start, end, e.getMessage());
            return "导出失败：" + e.getMessage();
        }
    }

    /**
     * 判断该类来源是否需要查询
     *
     * @param query  查询请求
     * @param source 来源
     * @return 需要查询返回 true
     */
    private boolean wanted(OperationLogQueryRequest query, String source) {
        return query.getSource() == null || query.getSource().isBlank()
                || source.equalsIgnoreCase(query.getSource().trim());
    }

    /**
     * 把提醒发送折成日志
     *
     * @param start      起
     * @param end        止
     * @param varietyMap 品种表，提醒不含品种，保留备用
     * @return 日志
     */
    private List<OperationLogVO> sendsOf(LocalDate start, LocalDate end, Map<String, VarietyVO> varietyMap) {
        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setStartDate(start);
        query.setEndDate(end);
        List<OperationLogVO> logs = new ArrayList<>();
        for (SendRecordVO record : sendRecordService.listSendRecord(query)) {
            OperationLogVO vo = new OperationLogVO();
            vo.setSource(SOURCE_SEND);
            vo.setSourceLabel("提醒");
            vo.setTradeDate(record.getSendDate());
            vo.setHappenedAt(record.getSentTime() == null
                    ? (record.getSendDate() == null ? null : record.getSendDate().atStartOfDay())
                    : record.getSentTime());
            vo.setEventType(pointLabel(record));
            vo.setStatus(record.getStatus() == null ? "未知" : record.getStatus().getLabel());
            vo.setNote(record.getFailReason() == null ? record.getContentPath() : record.getFailReason());
            logs.add(vo);
        }
        return logs;
    }

    /**
     * 把买卖变动折成日志
     *
     * @param start      起
     * @param end        止
     * @param varietyKey 只看该品种
     * @param varietyMap 品种表
     * @return 日志
     */
    private List<OperationLogVO> changesOf(LocalDate start, LocalDate end, String varietyKey,
                                           Map<String, VarietyVO> varietyMap) {
        HoldingChangeQueryRequest query = new HoldingChangeQueryRequest();
        query.setVarietyKey(varietyKey);
        List<OperationLogVO> logs = new ArrayList<>();
        for (HoldingChangeVO change : holdingService.listChanges(query)) {
            if (change.getOpenDate() == null
                    || change.getOpenDate().isBefore(start) || change.getOpenDate().isAfter(end)) {
                continue;
            }
            VarietyVO variety = varietyMap.get(change.getVarietyKey());
            OperationLogVO vo = new OperationLogVO();
            vo.setSource(SOURCE_CHANGE);
            vo.setSourceLabel("买卖");
            vo.setTradeDate(change.getOpenDate());
            vo.setHappenedAt(change.getCreateTime() == null
                    ? change.getOpenDate().atStartOfDay() : change.getCreateTime());
            vo.setEventType(change.getChangeType() == HoldingChangeType.BUY ? "买入" : "卖出");
            vo.setVarietyKey(change.getVarietyKey());
            vo.setVarietyCode(variety == null ? null : variety.getVarietyCode());
            vo.setVarietyName(variety == null ? change.getVarietyName() : variety.getVarietyName());
            vo.setStatus("成功");
            vo.setShares(change.getShares());
            vo.setUnitPrice(change.getUnitPrice());
            vo.setAmount(change.getAmount());
            vo.setNote(change.getNote());
            logs.add(vo);
        }
        return logs;
    }

    /**
     * 时点的中文名，带上具体时点便于核对
     *
     * @param record 发送记录
     * @return 事件类型
     */
    private String pointLabel(SendRecordVO record) {
        String point = record.getSendPoint() == null ? "提醒" : record.getSendPoint().getLabel();
        return point + "提醒";
    }

    /**
     * 取品种表供买卖日志带出名称
     *
     * @return 业务主键到品种的映射
     */
    private Map<String, VarietyVO> varietyMap() {
        Map<String, VarietyVO> map = new LinkedHashMap<>();
        List<VarietyVO> varieties = varietyService.listVariety(new VarietyQueryRequest());
        if (varieties != null) {
            for (VarietyVO variety : varieties) {
                if (variety.getVarietyKey() != null) {
                    map.put(variety.getVarietyKey(), variety);
                }
            }
        }
        return map;
    }

    /**
     * CSV 表头
     *
     * @return 表头
     */
    private List<String> header() {
        return List.of("发生时间", "业务日期", "来源", "事件", "代码", "名称",
                "结果", "份额", "成交单价", "金额", "说明");
    }

    /**
     * CSV 行
     *
     * @param logs 日志
     * @return 行数据
     */
    private List<List<String>> rows(List<OperationLogVO> logs) {
        List<List<String>> rows = new ArrayList<>();
        for (OperationLogVO log : logs) {
            rows.add(List.of(
                    log.getHappenedAt() == null ? "" : log.getHappenedAt().toString(),
                    log.getTradeDate() == null ? "" : log.getTradeDate().toString(),
                    text(log.getSourceLabel()),
                    text(log.getEventType()),
                    text(log.getVarietyCode()),
                    text(log.getVarietyName()),
                    text(log.getStatus()),
                    plain(log.getShares()),
                    plain(log.getUnitPrice()),
                    plain(log.getAmount()),
                    text(log.getNote())));
        }
        return rows;
    }

    /**
     * 数值转文本，不带千分位
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(java.math.BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 空值转空串
     *
     * @param value 原值
     * @return 文本
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
