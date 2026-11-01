package com.lilac.adapter.alipay;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付宝账单明细解析器，按官方导出格式读出逐行记录，不做业务归类
 */
@Slf4j
@Component
public class AlipayBillParser {

    /**
     * 表头之前的说明行有 4 行：标题、账号、起止日期、分隔线
     */
    private static final int HEADER_SCAN_LIMIT = 20;

    /**
     * 版本号列由若干位数字构成，用来把数据行与表尾合计行区分开
     */
    private static final int MIN_TRADE_NO_LENGTH = 10;

    /**
     * 表头里用于定位列的固定文字，改版时只认列名不认列号
     */
    private static final String COL_TRADE_NO = "交易号";
    private static final String COL_CREATED = "交易创建时间";
    private static final String COL_PAID = "付款时间";
    private static final String COL_COUNTERPARTY = "交易对方";
    private static final String COL_PRODUCT = "商品名称";
    private static final String COL_AMOUNT = "金额（元）";
    private static final String COL_DIRECTION = "收/支";
    private static final String COL_STATUS = "交易状态";
    private static final String COL_NOTE = "备注";
    private static final String COL_FUND_STATUS = "资金状态";

    /**
     * 官方导出带秒，手工改过的可能只到分，两种都认
     */
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"));

    /**
     * 只认日期不认时间的写法，个别导出没有时间列
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"));

    /**
     * 官方导出是 GBK，网页版另存可能是 UTF-8，两种都试
     */
    private static final List<Charset> CHARSETS = List.of(
            Charset.forName("GBK"), StandardCharsets.UTF_8);

    /**
     * 把账单文件字节解析成逐行记录，表头找不到即判定格式不符
     *
     * @param bytes 文件字节
     * @return 数据行
     */
    public List<AlipayBillRow> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR, "账单文件是空的");
        }
        String text = decode(bytes);
        List<String> lines = text.lines().toList();
        int headerIndex = findHeader(lines);
        if (headerIndex < 0) {
            throw new BusinessException(HttpsCodeEnum.PARAMS_ERROR,
                    "没找到「交易号 / 商品名称 / 金额（元）」表头，确认导出的是支付宝交易记录明细");
        }
        List<String> header = splitLine(lines.get(headerIndex));
        ColumnIndex index = ColumnIndex.of(header);
        List<AlipayBillRow> rows = new ArrayList<>();
        for (int cursor = headerIndex + 1; cursor < lines.size(); cursor++) {
            String line = lines.get(cursor);
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> cells = splitLine(line);
            // 表尾的合计行、分隔线与导出时间都不是交易数据
            if (cells.size() < header.size() || !looksLikeTradeNo(cells.get(0))) {
                continue;
            }
            rows.add(toRow(cells, index));
        }
        log.info("支付宝账单解析完成，共 {} 行", rows.size());
        return rows;
    }

    /**
     * 按候选字符集解码，能读出表头的那一个即是正确编码
     *
     * @param bytes 文件字节
     * @return 文本
     */
    private String decode(byte[] bytes) {
        for (Charset charset : CHARSETS) {
            String text = new String(bytes, charset);
            if (text.contains(COL_TRADE_NO) && text.contains(COL_PRODUCT)) {
                return text;
            }
        }
        // 两种都不像时按 GBK 兜底，后续会因找不到表头给出可读报错
        return new String(bytes, CHARSETS.get(0));
    }

    /**
     * 找表头所在行号，找不到返回 -1
     *
     * @param lines 全部行
     * @return 行号
     */
    private int findHeader(List<String> lines) {
        int limit = Math.min(lines.size(), HEADER_SCAN_LIMIT);
        for (int index = 0; index < limit; index++) {
            String line = lines.get(index);
            if (line == null) {
                continue;
            }
            if (line.contains(COL_TRADE_NO) && line.contains(COL_PRODUCT)
                    && line.contains(COL_AMOUNT)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 按分隔符拆列并去掉包裹的引号，引号内的分隔符属于内容
     *
     * @param line 原始行
     * @return 列文本
     */
    private List<String> splitLine(String line) {
        // 官方导出是逗号分隔；手工另存成制表符的也认，按出现更多的那种切
        char delimiter = line.indexOf('\t') >= 0 && line.indexOf(',') < 0 ? '\t' : ',';
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                // 连续两个引号是转义后的一个引号
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (ch == delimiter && !quoted) {
                cells.add(clean(current.toString()));
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        cells.add(clean(current.toString()));
        // 导出每行末尾多一个空列，去掉以便与表头对齐
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    /**
     * 去掉列文本两侧空白，含导出用于对齐的制表符填充
     *
     * @param raw 原始文本
     * @return 修剪后的文本
     */
    private String clean(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /**
     * 判断文本是否像交易号：纯数字，或退款行的「原交易号_退款交易号」复合形式
     *
     * @param text 文本
     * @return 像则返回 true
     */
    private boolean looksLikeTradeNo(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) {
            return false;
        }
        // 退款行把原交易号与退款交易号用下划线拼起来，取第一段判长度
        String head = value.split("_", 2)[0];
        if (head.length() < MIN_TRADE_NO_LENGTH) {
            return false;
        }
        boolean digits = head.chars().allMatch(Character::isDigit);
        return digits || value.chars().allMatch(ch -> Character.isDigit(ch) || ch == '_');
    }

    /**
     * 把一行的各列转成记录对象
     *
     * @param cells 列文本
     * @param index 列位置
     * @return 记录
     */
    private AlipayBillRow toRow(List<String> cells, ColumnIndex index) {
        AlipayBillRow row = new AlipayBillRow();
        row.setCells(new ArrayList<>(cells));
        row.setTradeNo(index.text(cells, index.tradeNo));
        row.setCreatedTime(index.time(cells, index.created));
        row.setPaidTime(index.time(cells, index.paid));
        row.setCounterparty(index.text(cells, index.counterparty));
        row.setProductName(index.text(cells, index.product));
        row.setRawAmount(index.text(cells, index.amount));
        row.setAmount(index.decimal(cells, index.amount));
        row.setDirection(index.text(cells, index.direction));
        row.setStatus(index.text(cells, index.status));
        row.setNote(index.text(cells, index.note));
        row.setFundStatus(index.text(cells, index.fundStatus));
        return row;
    }

    /**
     * 列位置，按表头文字定位，改版后列顺序变化也不受影响
     */
    private static final class ColumnIndex {

        private int tradeNo = -1;
        private int created = -1;
        private int paid = -1;
        private int counterparty = -1;
        private int product = -1;
        private int amount = -1;
        private int direction = -1;
        private int status = -1;
        private int note = -1;
        private int fundStatus = -1;

        /**
         * 按表头列名建索引
         *
         * @param header 表头列文本
         * @return 索引
         */
        static ColumnIndex of(List<String> header) {
            ColumnIndex index = new ColumnIndex();
            for (int position = 0; position < header.size(); position++) {
                String name = header.get(position).replace(" ", "");
                if (name.startsWith(COL_TRADE_NO)) {
                    index.tradeNo = position;
                } else if (name.startsWith(COL_CREATED)) {
                    index.created = position;
                } else if (name.startsWith(COL_PAID)) {
                    index.paid = position;
                } else if (name.startsWith(COL_COUNTERPARTY)) {
                    index.counterparty = position;
                } else if (name.startsWith(COL_PRODUCT)) {
                    index.product = position;
                } else if (name.startsWith(COL_AMOUNT)) {
                    index.amount = position;
                } else if (name.startsWith(COL_DIRECTION)) {
                    index.direction = position;
                } else if (name.startsWith(COL_STATUS)) {
                    index.status = position;
                } else if (name.startsWith(COL_NOTE)) {
                    index.note = position;
                } else if (name.startsWith(COL_FUND_STATUS)) {
                    index.fundStatus = position;
                }
            }
            return index;
        }

        /**
         * 取某列的文本，列不存在或越界时给空串
         *
         * @param cells 列文本
         * @param at    列号
         * @return 文本
         */
        String text(List<String> cells, int at) {
            if (at < 0 || at >= cells.size()) {
                return "";
            }
            return cells.get(at);
        }

        /**
         * 取某列的时间，只到日期时补 00:00，格式不认识时给 null
         *
         * @param cells 列文本
         * @param at    列号
         * @return 时间
         */
        LocalDateTime time(List<String> cells, int at) {
            String value = text(cells, at);
            if (value.isEmpty()) {
                return null;
            }
            for (DateTimeFormatter format : TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(value, format);
                } catch (DateTimeParseException e) {
                    // 换下一种格式继续试
                }
            }
            for (DateTimeFormatter format : DATE_FORMATS) {
                try {
                    LocalDate date = LocalDate.parse(value, format);
                    return LocalDateTime.of(date, LocalTime.MIN);
                } catch (DateTimeParseException e) {
                    // 换下一种格式继续试
                }
            }
            // 时间列格式变化不该让整份账单失败，留空由上层判定
            return null;
        }

        /**
         * 取某列的金额，非法数值给 null
         *
         * @param cells 列文本
         * @param at    列号
         * @return 金额
         */
        BigDecimal decimal(List<String> cells, int at) {
            String value = text(cells, at).replace(",", "");
            if (value.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
