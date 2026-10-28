package com.lilac.adapter.csindex;

import com.lilac.adapter.http.BinaryFetcher;
import com.lilac.adapter.http.BinaryResponse;
import com.lilac.adapter.model.IndexDividendPoint;
import com.lilac.adapter.model.IndexDividendSnapshot;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 中证指数官网估值文件适配器，用 HSSF 解析老式 .xls 里的股息率
 */
@Slf4j
@Component
public class CsIndexIndicatorAdapter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DATE_COLUMN = "日期";
    private static final String CODE_COLUMN = "指数代码";
    private static final String NAME_COLUMN = "指数中文全称";
    private static final String DIVIDEND_1_COLUMN = "股息率1";
    private static final String DIVIDEND_2_COLUMN = "股息率2";
    private static final int HEADER_ROW_INDEX = 0;
    private static final int KEPT_POINTS = 20;
    // 404 时官网返回一张 JPEG 错误图而非空文件，必须先看魔数再看状态码
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private final BinaryFetcher binaryFetcher;
    private final String indicatorUrlTemplate;

    /**
     * 注入二进制取数接口与估值文件地址模板
     *
     * @param binaryFetcher         二进制取数接口
     * @param indicatorUrlTemplate  地址模板，含 {code}
     */
    public CsIndexIndicatorAdapter(BinaryFetcher binaryFetcher,
                                   @Value("${fund.adapter.csindex.indicator-url}") String indicatorUrlTemplate) {
        this.binaryFetcher = binaryFetcher;
        this.indicatorUrlTemplate = indicatorUrlTemplate;
    }

    /**
     * 抓取并解析一只指数的估值文件，不存在时返回 null 交给手工覆盖
     *
     * @param indexCode 指数代码，如 000922
     * @return 快照，官网未收录该指数时返回 null
     */
    public IndexDividendSnapshot fetch(String indexCode) {
        String url = indicatorUrlTemplate.replace("{code}", indexCode);
        BinaryResponse response = binaryFetcher.fetch(url, Map.of());
        String contentType = response.getContentType() == null ? "" : response.getContentType();
        if (!response.ok()) {
            // 国证系列（如 399321）不在覆盖范围，返回 404 与一张 JPEG，属预期情况
            log.info("中证估值文件不可用 code={} 状态 {}", indexCode, response.getStatus());
            return null;
        }
        if (contentType.contains("image")) {
            log.warn("中证估值文件返回图片而非 Excel code={} 内容类型 {}", indexCode, contentType);
            return null;
        }
        if (!isOle2(response.getBody())) {
            throw new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                    "中证估值文件不是老式 .xls code=" + indexCode + "，格式已变化");
        }
        IndexDividendSnapshot snapshot = parse(indexCode, response.getBody());
        log.info("股息率解析完成 code={} 名称={} 数据日期={} 股息率={} 期数={}",
                indexCode, snapshot.getIndexName(), snapshot.getDataDate(),
                snapshot.getDividendYield(), snapshot.getPoints().size());
        return snapshot;
    }

    /**
     * 解析老式 .xls 字节，按表头列名定位而不是写死下标
     *
     * @param indexCode 指数代码
     * @param content   文件字节
     * @return 快照
     */
    IndexDividendSnapshot parse(String indexCode, byte[] content) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(HEADER_ROW_INDEX);
            if (header == null) {
                throw broken(indexCode, "缺少表头");
            }
            int dateIndex = indexOfColumn(header, DATE_COLUMN);
            int codeIndex = indexOfColumn(header, CODE_COLUMN);
            int nameIndex = indexOfColumn(header, NAME_COLUMN);
            int yield1Index = indexOfColumn(header, DIVIDEND_1_COLUMN);
            int yield2Index = indexOfColumn(header, DIVIDEND_2_COLUMN);
            if (dateIndex < 0 || codeIndex < 0 || yield1Index < 0 || yield2Index < 0) {
                throw broken(indexCode, "表头缺少日期、指数代码或股息率列");
            }
            List<IndexDividendPoint> points = new ArrayList<>();
            for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                IndexDividendPoint point = parseRow(sheet.getRow(rowIndex), indexCode,
                        dateIndex, codeIndex, nameIndex, yield1Index, yield2Index);
                if (point != null) {
                    points.add(point);
                }
            }
            if (points.isEmpty()) {
                throw broken(indexCode, "未解析到任何股息率数据行");
            }
            points.sort(Comparator.comparing(IndexDividendPoint::getDataDate).reversed());
            IndexDividendPoint latest = points.get(0);
            IndexDividendSnapshot snapshot = new IndexDividendSnapshot();
            snapshot.setIndexCode(latest.getIndexCode());
            snapshot.setIndexName(latest.getIndexName());
            snapshot.setDataDate(latest.getDataDate());
            snapshot.setDividendYield(latest.usableYield());
            snapshot.setPoints(points.subList(0, Math.min(KEPT_POINTS, points.size())));
            return snapshot;
        } catch (IOException e) {
            throw broken(indexCode, "文件无法按 .xls 解析：" + e.getMessage());
        }
    }

    /**
     * 解析一行，日期或股息率缺失时返回 null 而不是编造数字
     *
     * @param row        行
     * @param indexCode  指数代码
     * @param dateIndex  日期列下标
     * @param codeIndex  指数代码列下标
     * @param nameIndex  中文全称列下标，可为 -1
     * @param yield1Index 股息率1 列下标
     * @param yield2Index 股息率2 列下标
     * @return 数据点，该行不可用时返回 null
     */
    private IndexDividendPoint parseRow(Row row, String indexCode, int dateIndex, int codeIndex,
                                        int nameIndex, int yield1Index, int yield2Index) {
        if (row == null) {
            return null;
        }
        LocalDate dataDate = parseDate(cellText(row.getCell(dateIndex)));
        BigDecimal yield1 = parseDecimal(cellText(row.getCell(yield1Index)));
        BigDecimal yield2 = parseDecimal(cellText(row.getCell(yield2Index)));
        if (dataDate == null || (yield1 == null && yield2 == null)) {
            return null;
        }
        String rowCode = cellText(row.getCell(codeIndex));
        IndexDividendPoint point = new IndexDividendPoint();
        // 文件里的代码为准，缺失时用请求代码兜底
        point.setIndexCode(rowCode == null || rowCode.isBlank() ? indexCode : rowCode);
        point.setDataDate(dataDate);
        point.setIndexName(nameIndex < 0 ? null : cellText(row.getCell(nameIndex)));
        point.setDividendYield1(yield1);
        point.setDividendYield2(yield2);
        return point;
    }

    /**
     * 解析日期，官网给的是 20261027 这种 8 位数字串
     *
     * @param text 单元格文本
     * @return 日期，无法解析返回 null
     */
    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String digits = text.trim().replace("-", "").replace("/", "");
        try {
            return LocalDate.parse(digits, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析数值，容忍百分号与空白
     *
     * @param text 单元格文本
     * @return 数值，无法解析返回 null
     */
    private BigDecimal parseDecimal(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace("%", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取单元格文本，数值型按原样输出避免科学计数法
     *
     * @param cell 单元格
     * @return 文本，空单元格返回 null
     */
    private String cellText(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        String value = cell.toString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 按列名找下标，表头是「中文名English」混排，用包含匹配
     *
     * @param header      表头行
     * @param columnToken 列名关键词
     * @return 下标，未命中返回 -1
     */
    private int indexOfColumn(Row header, String columnToken) {
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String text = cellText(header.getCell(i));
            if (text != null && text.contains(columnToken)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断是否为 OLE2 复合文档，即老式 .xls
     *
     * @param content 文件字节
     * @return 是返回 true
     */
    private boolean isOle2(byte[] content) {
        if (content == null || content.length < OLE2_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < OLE2_MAGIC.length; i++) {
            if (content[i] != OLE2_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构造结构变化异常
     *
     * @param indexCode 指数代码
     * @param reason    原因
     * @return 业务异常
     */
    private BusinessException broken(String indexCode, String reason) {
        return new BusinessException(HttpsCodeEnum.DATA_SOURCE_BROKEN,
                "中证估值文件结构变化 code=" + indexCode + "：" + reason);
    }
}
