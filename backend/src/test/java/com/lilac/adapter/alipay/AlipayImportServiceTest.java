package com.lilac.adapter.alipay;

import com.lilac.domain.entity.FundCatalog;
import com.lilac.domain.entity.Variety;
import com.lilac.mapper.VarietyMapper;
import com.lilac.service.FundCatalogService;
import com.lilac.service.impl.AlipayImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 账单导入测试，重点是退款抵消、同交易号去重、以及名称到代码的匹配判定
 */
class AlipayImportServiceTest {

    private static final String HEAD = """
            支付宝交易记录明细查询\t\t
            账号:[15082597728]\t\t
            ---------------------------------交易记录明细列表------------------------------------\t\t
            """;

    private static final String HEADER = """
            交易号                  \t商家订单号               \t交易创建时间              \t付款时间                \t最近修改时间              \t交易来源地     \t类型              \t交易对方            \t商品名称                \t金额（元）   \t收/支     \t交易状态    \t服务费（元）   \t成功退款（元）  \t备注                  \t资金状态     \t
            """;

    private AlipayImportServiceImpl service;

    /**
     * 装配服务，基金全集与品种表用桩替代
     */
    @BeforeEach
    void setUp() {
        service = new AlipayImportServiceImpl();
        ReflectionTestUtils.setField(service, "alipayBillParser", new AlipayBillParser());
        ReflectionTestUtils.setField(service, "alipayFundClassifier", new AlipayFundClassifier());
        ReflectionTestUtils.setField(service, "fundNameMatcher", new FundNameMatcher());

        FundCatalogService catalogService = mock(FundCatalogService.class);
        when(catalogService.searchByName(anyString(), anyInt()))
                .thenAnswer(invocation -> catalogOf(invocation.getArgument(0)));
        ReflectionTestUtils.setField(service, "fundCatalogService", catalogService);

        VarietyMapper varietyMapper = mock(VarietyMapper.class);
        when(varietyMapper.selectListByQuery(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(variety("OF:011370", "011370", "华商均衡成长混合C")));
        ReflectionTestUtils.setField(service, "varietyMapper", varietyMapper);

        // 默认没有任何已导入记录，各用例按需覆盖
        ReflectionTestUtils.setField(service, "holdingChangeMapper", importedMapper(null));
    }

    /**
     * 同一交易号上的买入与买入退款必须一起消失，否则份额会虚高
     */
    @Test
    @DisplayName("退款行抵消同交易号的买入")
    void refundCancelsBuy() {
        List<AlipayImportRow> rows = preview(
                row("2026010000000000000006", "2026/10/27 10:20", "国泰黄金ETF联接C-买入", "9.39"),
                row("2026010000000000000006", "2026/10/27 10:26", "国泰黄金ETF联接C-买入退款", "9.39"));

        assertThat(rows).isEmpty();
    }

    /**
     * 同一张账单被重复导出时，完全相同的一行只留一条
     */
    @Test
    @DisplayName("重复导出的同一笔只留一条")
    void duplicateRowsCollapsed() {
        List<AlipayImportRow> rows = preview(
                row("2026010000000000000007", "2026/9/21 13:49", "蚂蚁财富-华商均衡成长混合C-买入", "20"),
                row("2026010000000000000007", "2026/9/21 13:49", "蚂蚁财富-华商均衡成长混合C-买入", "20"));

        assertThat(rows).hasSize(1);
    }

    /**
     * 名称精确命中且品种已注册的行默认可勾选
     */
    @Test
    @DisplayName("已注册且命中的买入默认可导入")
    void matchedRowSelectable() {
        List<AlipayImportRow> rows = preview(
                row("2026010000000000000008", "2026/10/12 14:21", "蚂蚁财富-华商均衡成长混合C-买入", "10"));

        assertThat(rows).hasSize(1);
        AlipayImportRow row = rows.get(0);
        assertThat(row.getFundCode()).isEqualTo("011370");
        assertThat(row.getVarietyKey()).isEqualTo("OF:011370");
        assertThat(row.getStatus()).isEqualTo("可导入");
        assertThat(row.getSelectable()).isTrue();
        // 14:21 在 15 点前，确认日就是当天
        assertThat(row.getConfirmDate()).hasToString("2026-10-12");
    }

    /**
     * 转换行拆成转出与转入两条：转出侧待补信息，转入侧走普通买入口径
     */
    @Test
    @DisplayName("转换行拆出转入侧，转出侧标为待补信息")
    void convertNeedsManual() {
        List<AlipayImportRow> rows = preview(row("2026010000000000000009", "2026/10/27 20:36",
                "蚂蚁财富-华商均衡成长混合C[转换至]华商均衡成长混合A", "403.32"));

        // 一条转换行必须产出两条：转出侧与转入侧，否则转入基金永远没有买入批次
        assertThat(rows).hasSize(2);
        AlipayImportRow out = rows.stream()
                .filter(item -> "CONVERT".equals(item.getAction())).findFirst().orElseThrow();
        AlipayImportRow in = rows.stream()
                .filter(item -> "CONVERT_IN".equals(item.getAction())).findFirst().orElseThrow();

        assertThat(out.getStatus()).isEqualTo("待补信息");
        assertThat(out.getSelectable()).isFalse();
        assertThat(out.getTargetFundName()).isEqualTo("华商均衡成长混合A");
        assertThat(out.getReason()).contains("转换");

        assertThat(in.getFundName()).isEqualTo("华商均衡成长混合A");
        assertThat(in.getAmount()).isEqualByComparingTo("403.32");
        assertThat(in.getTradeNo()).endsWith("#IN");
        // 转入基金没注册时先要人选，选定注册后才走「转换转入」那段说明
        assertThat(in.getCandidates()).isNotEmpty();
        assertThat(in.getCandidates()).extracting(AlipayImportRow.Candidate::getFundCode)
                .contains("011371");
    }

    /**
     * 库里已有同交易号的变动记录时，该行直接标「已导入」且不可勾选
     */
    @Test
    @DisplayName("已导入过的交易号自动标记并禁止重复勾选")
    void importedTradeNoSkipped() {
        // 先让桩的变动表返回一条带交易号的记录
        org.springframework.test.util.ReflectionTestUtils.setField(service, "holdingChangeMapper",
                importedMapper("2026010000000000000050"));

        List<AlipayImportRow> rows = preview(row("2026010000000000000050", "2026/10/12 14:21",
                "蚂蚁财富-华商均衡成长混合C-买入", "10"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("已导入");
        assertThat(rows.get(0).getSelectable()).isFalse();
        assertThat(rows.get(0).getReason()).contains("已存在");
    }

    /**
     * 造一个只返回指定交易号的变动表桩，传 null 表示库里还没有任何导入记录
     *
     * @param tradeNo 已有交易号，可为 null
     * @return 桩
     */
    private com.lilac.mapper.HoldingChangeMapper importedMapper(String tradeNo) {
        com.lilac.mapper.HoldingChangeMapper mapper = mock(com.lilac.mapper.HoldingChangeMapper.class);
        if (tradeNo == null) {
            when(mapper.selectListByQuery(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            return mapper;
        }
        com.lilac.domain.entity.HoldingChange change = new com.lilac.domain.entity.HoldingChange();
        change.setTradeNo(tradeNo);
        when(mapper.selectListByQuery(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(change));
        return mapper;
    }

    /**
     * 全集里查不到的名称要明说查不到，不能悄悄给个空代码
     */
    @Test
    @DisplayName("未匹配的基金给出可读原因")
    void unmatchedExplained() {
        List<AlipayImportRow> rows = preview(row("2026010000000000000010", "2026/10/12 14:21",
                "蚂蚁财富-查无此基金-买入", "10"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("未匹配");
        assertThat(rows.get(0).getReason()).contains("查无此基金");
    }

    /**
     * 现金分红要明确说明只留档不参与持仓计算
     */
    @Test
    @DisplayName("现金分红说明不计入持仓")
    void cashDividendExplained() {
        List<AlipayImportRow> rows = preview(row("2026010000000000000014", "2026/10/19 15:39",
                "蚂蚁财富-华商均衡成长混合C-现金分红至余额宝", "0.24"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("CASH_DIVIDEND");
        assertThat(rows.get(0).getReason()).contains("只留档");
    }

    /**
     * 桩：按名称返回全集行，只有含「华商均衡成长混合」的才查得到
     *
     * @param keyword 名称关键词
     * @return 全集行
     */
    private List<FundCatalog> catalogOf(String keyword) {
        if (!keyword.contains("华商均衡成长混合")) {
            return List.of();
        }
        return List.of(catalog("011370", "华商均衡成长混合C"), catalog("011371", "华商均衡成长混合A"));
    }

    /**
     * 造一条全集行
     *
     * @param code 代码
     * @param name 名称
     * @return 全集行
     */
    private FundCatalog catalog(String code, String name) {
        FundCatalog catalog = new FundCatalog();
        catalog.setFundCode(code);
        catalog.setFundName(name);
        catalog.setFundType("混合型-偏股");
        return catalog;
    }

    /**
     * 造一条已注册品种
     *
     * @param key  业务主键
     * @param code 6 位代码
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
     * 走一遍解析与匹配，返回待确认行
     *
     * @param lines 账单原始行
     * @return 待确认行
     */
    private List<AlipayImportRow> preview(String... lines) {
        String text = HEAD + HEADER + String.join("\n", lines);
        return service.preview(text.getBytes(Charset.forName("GBK")), "test.csv");
    }

    /**
     * 拼一行官方格式的记录
     *
     * @param tradeNo 交易号
     * @param time    交易创建时间
     * @param product 商品名称
     * @param amount  金额
     * @return 原始行
     */
    private String row(String tradeNo, String time, String product, String amount) {
        return tradeNo + "\t\t" + time + "\t\t" + time
                + "\t支付宝网站\t即时到账交易\t蚂蚁财富-蚂蚁（杭州）基金销售有限公司\t" + product
                + "\t" + amount + "\t不计收支\t交易成功\t0\t0\t\t已支出";
    }
}
