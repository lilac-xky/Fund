package com.lilac.service.impl;

import com.lilac.domain.dto.estimate.EstimateErrorQueryRequest;
import com.lilac.domain.dto.variety.VarietyQueryRequest;
import com.lilac.domain.vo.EstimateErrorVO;
import com.lilac.domain.vo.ReportRowVO;
import com.lilac.domain.vo.ReviewReportVO;
import com.lilac.domain.vo.VarietyVO;
import com.lilac.report.CsvWriter;
import com.lilac.service.EstimateErrorService;
import com.lilac.service.ReviewReportService;
import com.lilac.service.VarietyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复盘报告业务实现，按日生成对照表、留存文本并导出 CSV
 */
@Slf4j
@Service
public class ReviewReportServiceImpl implements ReviewReportService {

    private static final int RESULT_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String GROUP_LABEL = "复盘";

    @Resource
    private com.lilac.config.DataRootResolver dataRootResolver;

    @Resource
    private EstimateErrorService estimateErrorService;

    @Resource
    private VarietyService varietyService;

    @Resource
    private CsvWriter csvWriter;

    /**
     * 生成复盘报告并留存为文本文件
     *
     * @param tradeDate 交易日
     * @return 复盘报告
     */
    @Override
    public ReviewReportVO generate(LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        List<EstimateErrorVO> errors = errorsOf(date, date);
        ReviewReportVO report = build(date, errors);
        report.setSavedPath(saveText(date, report));
        return report;
    }

    /**
     * 导出预估与实际对照 CSV
     *
     * @param tradeDate 交易日
     * @return 复盘报告，savedPath 指向 CSV
     */
    @Override
    public ReviewReportVO exportComparison(LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        List<EstimateErrorVO> errors = errorsOf(date, date);
        ReviewReportVO report = build(date, errors);
        Path target = reportDir().resolve("compare-" + date + ".csv");
        try {
            csvWriter.write(target, comparisonHeader(), comparisonRows(report));
            report.setSavedPath(target.toString());
        } catch (IOException e) {
            log.warn("对照 CSV 导出失败 日期={}：{}", date, e.getMessage());
            report.getNotes().add("CSV 导出失败：" + e.getMessage());
        }
        return report;
    }

    /**
     * 导出误差历史区间 CSV
     *
     * @param startDate 起始日
     * @param endDate   截止日
     * @return 导出说明
     */
    @Override
    public String exportErrorHistory(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        List<EstimateErrorVO> errors = errorsOf(start, end);
        Path target = reportDir().resolve("error-history-" + start + "_" + end + ".csv");
        try {
            int written = csvWriter.write(target, errorHistoryHeader(), errorHistoryRows(errors));
            return "已导出 " + written + " 行到 " + target;
        } catch (IOException e) {
            log.warn("误差历史导出失败 {}~{}：{}", start, end, e.getMessage());
            return "导出失败：" + e.getMessage();
        }
    }

    /**
     * 按交易日取误差记录
     *
     * @param start 起始日
     * @param end   截止日
     * @return 误差记录
     */
    private List<EstimateErrorVO> errorsOf(LocalDate start, LocalDate end) {
        EstimateErrorQueryRequest request = new EstimateErrorQueryRequest();
        request.setStartDate(start);
        request.setEndDate(end);
        List<EstimateErrorVO> rows = estimateErrorService.listEstimateError(request);
        return rows == null ? List.of() : rows;
    }

    /**
     * 组装复盘报告，含逐只对照与误差分布统计
     *
     * @param date   交易日
     * @param errors 误差记录
     * @return 报告
     */
    private ReviewReportVO build(LocalDate date, List<EstimateErrorVO> errors) {
        Map<String, VarietyVO> varietyMap = varietyMap();
        List<ReportRowVO> rows = new ArrayList<>(errors.size());
        List<BigDecimal> absErrors = new ArrayList<>();
        int calibrated = 0;
        int overThreshold = 0;
        int directionHit = 0;
        int directionTotal = 0;
        for (EstimateErrorVO error : errors) {
            ReportRowVO row = new ReportRowVO();
            VarietyVO variety = varietyMap.get(error.getVarietyKey());
            row.setVarietyKey(error.getVarietyKey());
            row.setVarietyCode(variety == null ? null : variety.getVarietyCode());
            row.setVarietyName(variety == null ? error.getVarietyKey() : variety.getVarietyName());
            row.setCategoryLabel(variety == null || variety.getCategory() == null ? "待确认"
                    : variety.getCategory().getLabel());
            row.setOutputTier(error.getOutputTier());
            row.setEstimatedChange(error.getEstimatedChange());
            row.setActualChange(error.getActualChange());
            row.setErrorValue(error.getErrorValue());
            row.setThresholdValue(error.getThresholdValue());
            row.setOverThreshold(error.getOverThreshold());
            row.setDirectionHit(error.getDirectionHit());
            row.setStatus(statusOf(error));
            rows.add(row);
            if (Boolean.TRUE.equals(error.getOverThreshold())) {
                overThreshold++;
            }
            if (error.getErrorValue() != null) {
                absErrors.add(error.getErrorValue().abs());
                calibrated++;
            }
            if (error.getDirectionHit() != null) {
                directionTotal++;
                if (Boolean.TRUE.equals(error.getDirectionHit())) {
                    directionHit++;
                }
            }
        }
        rows.sort(Comparator.comparing(ReportRowVO::getVarietyName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        ReviewReportVO report = new ReviewReportVO();
        report.setTradeDate(date);
        report.setGeneratedAt(LocalDateTime.now().format(CLOCK));
        report.setRows(rows);
        report.setEstimatedCount(rows.size());
        report.setCalibratedCount(calibrated);
        report.setOverThresholdCount(overThreshold);
        report.setMedianAbsError(medianOf(absErrors));
        report.setP90AbsError(p90Of(absErrors));
        report.setMaxAbsError(absErrors.isEmpty() ? null
                : absErrors.stream().max(Comparator.naturalOrder()).orElse(null));
        report.setDirectionAccuracy(directionTotal == 0 ? null
                : BigDecimal.valueOf(directionHit).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(directionTotal), PERCENT_SCALE, RoundingMode.HALF_UP));
        report.setNotes(notesOf(report, directionTotal));
        return report;
    }

    /**
     * 拼报告说明，把未校准与超阈值两类情况讲清楚
     *
     * @param report         报告
     * @param directionTotal 参与方向统计的只数
     * @return 说明
     */
    private List<String> notesOf(ReviewReportVO report, int directionTotal) {
        List<String> notes = new ArrayList<>();
        if (report.getRows().isEmpty()) {
            notes.add("当日没有误差记录：晚间校准可能尚未执行，或当日无持仓");
            return notes;
        }
        notes.add("共 " + report.getEstimatedCount() + " 只，已对照 " + report.getCalibratedCount()
                + " 只，超阈值 " + report.getOverThresholdCount() + " 只");
        if (report.getMedianAbsError() != null) {
            notes.add("误差绝对值中位数 " + percent(report.getMedianAbsError()) + "，P90 "
                    + percent(report.getP90AbsError()) + "，最大 " + percent(report.getMaxAbsError()));
        }
        if (directionTotal == 0) {
            notes.add("当日没有方向判定数据，方向命中率不可算");
        } else {
            notes.add("方向命中率 " + percent(report.getDirectionAccuracy()) + "（参与 " + directionTotal + " 只）");
        }
        int uncalibrated = report.getEstimatedCount() - report.getCalibratedCount();
        if (uncalibrated > 0) {
            notes.add("有 " + uncalibrated + " 只没有误差值，原因见逐只状态列");
        }
        return notes;
    }

    /**
     * 单只记录的状态说明
     *
     * @param error 误差记录
     * @return 状态
     */
    private String statusOf(EstimateErrorVO error) {
        if (error.getErrorValue() == null) {
            return "无点估计，仅比对方向或区间";
        }
        if (Boolean.TRUE.equals(error.getOverThreshold())) {
            return "超阈值";
        }
        return "正常";
    }

    /**
     * 把报告写成本地文本，失败只记日志不影响接口返回
     *
     * @param date   交易日
     * @param report 报告
     * @return 文件路径，失败返回 null
     */
    private String saveText(LocalDate date, ReviewReportVO report) {
        Path target = reportDir().resolve("review-" + date + ".txt");
        List<String> lines = new ArrayList<>();
        lines.add(GROUP_LABEL + "报告 " + date);
        lines.add("生成时间：" + report.getGeneratedAt());
        lines.add("");
        for (String note : report.getNotes()) {
            lines.add(note);
        }
        lines.add("");
        lines.add("代码\t名称\t类型\t档位\t预估\t实际\t误差\t阈值\t状态");
        for (ReportRowVO row : report.getRows()) {
            lines.add(String.join("\t",
                    text(row.getVarietyCode()), text(row.getVarietyName()), text(row.getCategoryLabel()),
                    text(row.getOutputTier()), percent(row.getEstimatedChange()),
                    percent(row.getActualChange()), percent(row.getErrorValue()),
                    percent(row.getThresholdValue()), text(row.getStatus())));
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, lines, StandardCharsets.UTF_8);
            return target.toString();
        } catch (IOException e) {
            log.warn("复盘报告留存失败 日期={}：{}", date, e.getMessage());
            return null;
        }
    }

    /**
     * 对照 CSV 的表头
     *
     * @return 表头
     */
    private List<String> comparisonHeader() {
        return List.of("交易日", "代码", "名称", "类型", "档位", "预估涨跌幅(%)",
                "实际涨跌幅(%)", "误差(%)", "阈值(%)", "是否超阈值", "方向命中", "状态");
    }

    /**
     * 对照 CSV 的行
     *
     * @param report 报告
     * @return 行数据
     */
    private List<List<String>> comparisonRows(ReviewReportVO report) {
        List<List<String>> rows = new ArrayList<>();
        for (ReportRowVO row : report.getRows()) {
            rows.add(List.of(text(report.getTradeDate()), text(row.getVarietyCode()),
                    text(row.getVarietyName()), text(row.getCategoryLabel()), text(row.getOutputTier()),
                    plain(row.getEstimatedChange()), plain(row.getActualChange()), plain(row.getErrorValue()),
                    plain(row.getThresholdValue()), bool(row.getOverThreshold()),
                    bool(row.getDirectionHit()), text(row.getStatus())));
        }
        return rows;
    }

    /**
     * 误差历史 CSV 的表头
     *
     * @return 表头
     */
    private List<String> errorHistoryHeader() {
        return List.of("交易日", "品种主键", "预估涨跌幅(%)", "实际涨跌幅(%)", "误差(%)",
                "阈值(%)", "是否超阈值", "方向命中", "区间命中", "区间宽度", "档位", "来源");
    }

    /**
     * 误差历史 CSV 的行
     *
     * @param errors 误差记录
     * @return 行数据
     */
    private List<List<String>> errorHistoryRows(List<EstimateErrorVO> errors) {
        List<List<String>> rows = new ArrayList<>();
        for (EstimateErrorVO error : errors) {
            rows.add(List.of(text(error.getTradeDate()), text(error.getVarietyKey()),
                    plain(error.getEstimatedChange()), plain(error.getActualChange()),
                    plain(error.getErrorValue()), plain(error.getThresholdValue()),
                    bool(error.getOverThreshold()), bool(error.getDirectionHit()),
                    bool(error.getInInterval()), plain(error.getIntervalWidth()),
                    text(error.getOutputTier()), text(error.getEstimateSource())));
        }
        return rows;
    }

    /**
     * 取品种表，供对照表带出名称与类型
     *
     * @return 业务主键到品种的映射
     */
    private Map<String, VarietyVO> varietyMap() {
        Map<String, VarietyVO> map = new LinkedHashMap<>();
        List<VarietyVO> varieties = varietyService.listVariety(new VarietyQueryRequest());
        if (varieties == null) {
            return map;
        }
        for (VarietyVO variety : varieties) {
            if (variety.getVarietyKey() != null) {
                map.put(variety.getVarietyKey(), variety);
            }
        }
        return map;
    }

    /**
     * 取报告目录，程序化创建避免首次写入才暴露路径问题
     *
     * @return 目录路径
     */
    private Path reportDir() {
        return dataRootResolver.root().resolve("report");
    }

    /**
     * 取中位数
     *
     * @param values 数值列表
     * @return 中位数，空列表返回 null
     */
    private BigDecimal medianOf(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
        }
        return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2), RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 取 P90，用最近秩法，样本少时不会插值出不存在的小数
     *
     * @param values 数值列表
     * @return P90，空列表返回 null
     */
    private BigDecimal p90Of(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(sorted.size() * 0.9) - 1;
        int clamped = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(clamped).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 百分比格式化，带符号并保留两位
     *
     * @param value 数值
     * @return 文本
     */
    private String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal scaled = value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString() + "%";
    }

    /**
     * 纯数值输出，CSV 里不夹百分号，便于 Excel 计算
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : trimmed.toPlainString();
    }

    /**
     * 空值给破折号，避免正文出现 null
     *
     * @param value 原值
     * @return 文本
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 布尔转「是 / 否」，空值留空
     *
     * @param value 布尔值
     * @return 文本
     */
    private String bool(Boolean value) {
        if (value == null) {
            return "";
        }
        return value ? "是" : "否";
    }
}
