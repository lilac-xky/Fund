package com.lilac.adapter.csindex;

import com.lilac.adapter.http.BinaryResponse;
import com.lilac.adapter.model.IndexDividendSnapshot;
import com.lilac.exception.BusinessException;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 股息率解析测试，用 HSSF 现造老式 .xls 复刻官网真实列结构
 */
class CsIndexIndicatorAdapterTest {

    private static final String URL_TEMPLATE =
            "https://oss-ch.csindex.com.cn/static/html/csindex/public/uploads/file/autofile/indicator/{code}indicator.xls";
    private static final String XLS_CONTENT_TYPE = "application/vnd.ms-excel";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    /**
     * 官网真实列结构下能取到最新一期股息率与数据日期
     */
    @Test
    @DisplayName("解析真实列结构取到股息率与数据日期")
    void parsesRealColumnLayout() {
        CsIndexIndicatorAdapter adapter = newAdapter(ok(realRows()));

        IndexDividendSnapshot snapshot = adapter.fetch("000922");

        assertThat(snapshot.getIndexCode()).isEqualTo("000922");
        assertThat(snapshot.getIndexName()).isEqualTo("中证红利指数");
        assertThat(snapshot.getDataDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(snapshot.getDividendYield()).isEqualByComparingTo("4.23");
        // 只保留近期序列，且按日期降序
        assertThat(snapshot.getPoints()).hasSize(3);
        assertThat(snapshot.getPoints().get(0).getDataDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(snapshot.getPoints().get(0).getDividendYield1()).isEqualByComparingTo("4.08");
        assertThat(snapshot.getPoints().get(0).getDividendYield2()).isEqualByComparingTo("4.23");
    }

    /**
     * 两档股息率齐全时优先计算用股本口径，缺失时退回总股本口径
     */
    @Test
    @DisplayName("两档股息率口径优先取计算用股本")
    void prefersComputedShareYield() {
        CsIndexIndicatorAdapter adapter = newAdapter(ok(new Object[][]{
                {"日期Date", "指数代码Index Code", "指数中文全称Chinese Name(Full)",
                        "股息率1（总股本）D/P1", "股息率2（计算用股本）D/P2"},
                {"20261027", "H30269", "中证红利低波动指数", 4.16, null},
                {"20261026", "H30269", "中证红利低波动指数", 4.18, 4.95}}));

        IndexDividendSnapshot snapshot = adapter.fetch("H30269");

        assertThat(snapshot.getDividendYield()).isEqualByComparingTo("4.16");
        assertThat(snapshot.getPoints().get(1).usableYield()).isEqualByComparingTo("4.95");
    }

    /**
     * 国证系列返回 404 与一张 JPEG，属预期情况，返回 null 交给手工覆盖
     */
    @Test
    @DisplayName("404 返回 JPEG 时落到手工覆盖路径")
    void notFoundFallsBackToManual() {
        CsIndexIndicatorAdapter adapter = newAdapter(new BinaryResponse(404, jpegBytes(), JPEG_CONTENT_TYPE));

        assertThat(adapter.fetch("399321")).isNull();
    }

    /**
     * 200 但内容类型是图片时同样视为未收录，不抛结构变化
     */
    @Test
    @DisplayName("200 返回图片时视为未收录")
    void imageBodyTreatedAsMissing() {
        CsIndexIndicatorAdapter adapter = newAdapter(new BinaryResponse(200, jpegBytes(), JPEG_CONTENT_TYPE));

        assertThat(adapter.fetch("399324")).isNull();
    }

    /**
     * 200 但字节不是 OLE2 时报结构变化，绝不返回空对象让上层误以为没数据
     */
    @Test
    @DisplayName("非老式 xls 报结构变化")
    void nonOle2BodyThrows() {
        CsIndexIndicatorAdapter adapter = newAdapter(new BinaryResponse(200,
                "<html>验证页</html>".getBytes(), "text/html"));

        assertThatThrownBy(() -> adapter.fetch("000922"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是老式 .xls");
    }

    /**
     * 表头缺股息率列时报结构变化，而不是静默返回 null 股息率
     */
    @Test
    @DisplayName("表头缺股息率列报结构变化")
    void missingColumnThrows() {
        CsIndexIndicatorAdapter adapter = newAdapter(ok(new Object[][]{
                {"日期Date", "指数代码Index Code", "指数中文全称Chinese Name(Full)", "市盈率1（总股本）P/E1"},
                {"20261027", "000922", "中证红利指数", 9.24}}));

        assertThatThrownBy(() -> adapter.fetch("000922"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少日期、指数代码或股息率列");
    }

    /**
     * 全部行都没有可用股息率时报结构变化，避免拿空数据参与策略
     */
    @Test
    @DisplayName("无可用数据行报结构变化")
    void emptyRowsThrows() {
        CsIndexIndicatorAdapter adapter = newAdapter(ok(new Object[][]{
                {"日期Date", "指数代码Index Code", "指数中文全称Chinese Name(Full)",
                        "股息率1（总股本）D/P1", "股息率2（计算用股本）D/P2"},
                {"", "", "", null, null}}));

        assertThatThrownBy(() -> adapter.fetch("000922"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未解析到任何股息率数据行");
    }

    /**
     * 造一份与官网一致的三行数据
     *
     * @return 行数据
     */
    private Object[][] realRows() {
        return new Object[][]{
                {"日期Date", "指数代码Index Code", "指数中文全称Chinese Name(Full)", "指数中文简称Index Chinese Name",
                        "指数英文全称English Name(Full)", "指数英文简称Index English Name",
                        "市盈率1（总股本）P/E1", "市盈率2（计算用股本）P/E2",
                        "股息率1（总股本）D/P1", "股息率2（计算用股本）D/P2"},
                {"20261027", "000922", "中证红利指数", "中证红利", "CSI Dividend Index", "CSI Dividend",
                        9.24, 10.8, 4.08, 4.23},
                {"20261026", "000922", "中证红利指数", "中证红利", "CSI Dividend Index", "CSI Dividend",
                        9.21, 10.74, 4.1, 4.25},
                {"20261023", "000922", "中证红利指数", "中证红利", "CSI Dividend Index", "CSI Dividend",
                        9.02, 10.61, 4.19, 4.31}};
    }

    /**
     * 造一个返回固定响应的适配器
     *
     * @param response 固定响应
     * @return 适配器
     */
    private CsIndexIndicatorAdapter newAdapter(BinaryResponse response) {
        return new CsIndexIndicatorAdapter((url, headers) -> response, URL_TEMPLATE);
    }

    /**
     * 构造成功响应
     *
     * @param rows 行数据
     * @return 二进制响应
     */
    private BinaryResponse ok(Object[][] rows) {
        return new BinaryResponse(200, xlsBytes(rows), XLS_CONTENT_TYPE);
    }

    /**
     * 用 HSSF 写出老式 .xls 字节，模拟官网文件格式
     *
     * @param rows 行数据，第一行为表头
     * @return 文件字节
     */
    private byte[] xlsBytes(Object[][] rows) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            HSSFSheet sheet = workbook.createSheet("indicator.xls");
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                HSSFRow row = sheet.createRow(rowIndex);
                for (int cellIndex = 0; cellIndex < rows[rowIndex].length; cellIndex++) {
                    Object value = rows[rowIndex][cellIndex];
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Number number) {
                        row.createCell(cellIndex).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(cellIndex).setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 构造一张伪造的 JPEG 头，复刻官网 404 时的返回
     *
     * @return 字节
     */
    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
    }
}
