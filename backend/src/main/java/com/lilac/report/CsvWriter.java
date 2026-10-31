package com.lilac.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 导出器，统一加 UTF-8 BOM 并按 RFC 4180 转义，保证 Excel 直接打开中文不乱码
 */
@Slf4j
@Component
public class CsvWriter {

    /**
     * Excel 在无 BOM 时按本地代码页解析 UTF-8，中文会乱码；这三个字节不能省
     */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String LINE_SEPARATOR = "\r\n";

    private static final char QUOTE = '"';

    /**
     * 把表头与行数据写成 CSV 文件，已存在时覆盖
     *
     * @param target 目标文件
     * @param header 表头
     * @param rows   行数据，每行长度须与表头一致
     * @return 写入的行数（不含表头）
     * @throws IOException 写入失败
     */
    public int write(Path target, List<String> header, List<List<String>> rows) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(line(header));
        for (List<String> row : rows) {
            lines.add(line(row));
        }
        String body = String.join(LINE_SEPARATOR, lines) + LINE_SEPARATOR;
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + content.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(content, 0, withBom, UTF8_BOM.length, content.length);
        Files.write(target, withBom);
        log.info("CSV 已导出 {} 行：{}", rows.size(), target);
        return rows.size();
    }

    /**
     * 拼一行，含逗号、引号或换行的字段用双引号包裹并把引号翻倍
     *
     * @param cells 字段
     * @return 一行文本
     */
    private String line(List<String> cells) {
        List<String> escaped = new ArrayList<>(cells.size());
        for (String cell : cells) {
            escaped.add(escape(cell));
        }
        return String.join(",", escaped);
    }

    /**
     * 转义单个字段，null 输出空串
     *
     * @param cell 字段值
     * @return 转义后文本
     */
    private String escape(String cell) {
        String value = cell == null ? "" : cell;
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf(QUOTE) >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return QUOTE + value.replace(String.valueOf(QUOTE), String.valueOf(QUOTE) + QUOTE) + QUOTE;
    }
}
