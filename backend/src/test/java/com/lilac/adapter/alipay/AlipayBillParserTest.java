package com.lilac.adapter.alipay;

import com.lilac.enums.AlipayAction;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付宝账单解析测试，用例形态取自使用者导出的真实明细
 */
class AlipayBillParserTest {

    /**
     * 表头前的四行说明与分隔线，官方导出固定有这些
     */
    private static final String HEAD = """
            支付宝交易记录明细查询\t\t
            账号:[15082597728]\t\t
            起始日期:[2026-01-01 00:00:00]    终止日期:[2026-11-01 16:42:54]\t\t
            ---------------------------------交易记录明细列表------------------------------------\t\t
            """;

    /**
     * 官方表头，列名用于定位，改版后列顺序变化也不受影响
     */
    private static final String HEADER = """
            交易号                  \t商家订单号               \t交易创建时间              \t付款时间                \t最近修改时间              \t交易来源地     \t类型              \t交易对方            \t商品名称                \t金额（元）   \t收/支     \t交易状态    \t服务费（元）   \t成功退款（元）  \t备注                  \t资金状态     \t
            """;

    private AlipayBillParser parser;
    private AlipayFundClassifier classifier;

    /**
     * 装配解析器与识别器
     */
    @BeforeEach
    void setUp() {
        parser = new AlipayBillParser();
        classifier = new AlipayFundClassifier();
    }

    /**
     * 引号里夹着 TAB 的买入行要能正确对齐，金额与时间不能串列
     */
    @Test
    @DisplayName("引号内 TAB 不影响列对齐")
    void quotedTabKeepsColumns() {
        String body = """
                "20260921001080012204880018794446\t"\t"                    \t"\t2026/9/21 13:49\t                    \t2026/9/21 13:49\t支付宝网站     \t即时到账交易          \t蚂蚁财富-蚂蚁（杭州）基金销售有限公司\t蚂蚁财富-华商均衡成长混合C-买入   \t20\t不计收支    \t交易成功    \t0\t0\t                    \t已支出      \t
                """;
        List<AlipayBillRow> rows = parser.parse(bytesOf(HEAD + HEADER + body));

        assertThat(rows).hasSize(1);
        AlipayBillRow row = rows.get(0);
        assertThat(row.getTradeNo()).isEqualTo("20260921001080012204880018794446");
        assertThat(row.getAmount()).isEqualByComparingTo("20");
        assertThat(row.getCreatedTime()).hasToString("2026-09-21T13:49");
        assertThat(row.getProductName()).isEqualTo("蚂蚁财富-华商均衡成长混合C-买入");
        assertThat(row.getFundStatus()).isEqualTo("已支出");
    }

    /**
     * 买入、卖出、分红、转换、退款五种形态都要能识别出对应动作
     */
    @Test
    @DisplayName("五种动作各自识别正确")
    void classifyActions() {
        Map<String, AlipayFundOrder> byNo = byTradeNo(
                row("2026011500000000000001", "2026/10/12 14:21", "蚂蚁财富-华商均衡成长混合C-买入",
                        "10"),
                row("2026011500000000000002", "2026/9/21 15:28",
                        "蚂蚁财富-华商均衡成长混合C-卖出至余额宝", "18.08"),
                row("2026011500000000000003", "2026/10/19 15:39",
                        "蚂蚁财富-华泰柏瑞中证红利低波动ETF联接C-现金分红至余额宝", "0.24"),
                row("2026011500000000000004", "2026/10/27 20:36",
                        "蚂蚁财富-中信保诚至泰中短债债券C[转换至]金信民兴债券C", "403.32"),
                row("2026011500000000000005", "2026/10/27 10:26", "国泰黄金ETF联接C-买入退款",
                        "9.39"));

        assertThat(byNo.get("2026011500000000000001").getAction()).isEqualTo(AlipayAction.BUY);
        assertThat(byNo.get("2026011500000000000002").getAction()).isEqualTo(AlipayAction.SELL);
        assertThat(byNo.get("2026011500000000000003").getAction())
                .isEqualTo(AlipayAction.CASH_DIVIDEND);
        assertThat(byNo.get("2026011500000000000004").getAction()).isEqualTo(AlipayAction.CONVERT);
        assertThat(byNo.get("2026011500000000000005").getAction()).isEqualTo(AlipayAction.REFUND);
    }

    /**
     * 动作词与平台前缀都要剥干净，转换行还要分出转入的那只
     */
    @Test
    @DisplayName("剥掉前缀与动作词，得到纯基金名称")
    void stripName() {
        Map<String, AlipayFundOrder> byNo = byTradeNo(
                row("2026011500000000000011", "2026/10/12 14:21", "蚂蚁财富-华商均衡成长混合C-买入   ",
                        "10"),
                row("2026011500000000000012", "2026/10/27 20:36",
                        "蚂蚁财富-中信保诚至泰中短债债券C[转换至]金信民兴债券C", "403.32"),
                row("2026011500000000000013", "2026/10/27 10:20", "国泰黄金ETF联接C-买入", "9.39"));

        assertThat(byNo.get("2026011500000000000011").getFundName()).isEqualTo("华商均衡成长混合C");
        assertThat(byNo.get("2026011500000000000012").getFundName())
                .isEqualTo("中信保诚至泰中短债债券C");
        assertThat(byNo.get("2026011500000000000012").getTargetFundName()).isEqualTo("金信民兴债券C");
        assertThat(byNo.get("2026011500000000000013").getFundName()).isEqualTo("国泰黄金ETF联接C");
    }

    /**
     * 净值日的判定规则按动作分流：
     * 买入与转换的创建时刻可信，15 点后顺延到下一交易日；
     * 卖出的创建时刻是到账时间，不可用，固定取交易号那天。
     *
     * <p>实测依据：持仓里 6-05 15:18 那笔买入的成本价是 3.8413 = 6-08 净值（次一交易日），
     * 证明买入 15 点后确实顺延；而 9-18 的卖出创建时间 9-21 15:28，实际按 9-18 净值成交，
     * 说明卖出的创建时刻只是到账时刻。</p>
     */
    @Test
    @DisplayName("买入按 15 点顺延，卖出固定取交易号日")
    void confirmDateByAction() {
        // 交易号以 20261023（周五）开头；2026-10-26 是周一
        LocalDate friday = LocalDate.of(2026, 10, 23);
        LocalDate monday = LocalDate.of(2026, 10, 26);
        Map<String, AlipayFundOrder> byNo = byTradeNo(
                // 买入 15:00 后：顺延到周一
                row("2026102300000000000022", "2026/10/23 15:00", "蚂蚁财富-某基金-买入", "20"),
                // 买入 15:00 前：当日
                row("2026102300000000000023", "2026/10/23 14:59", "蚂蚁财富-某基金-买入", "20"),
                // 卖出创建时间即使到了下周二，仍取交易号那天
                row("2026102300000000000024", "2026/10/27 15:28",
                        "蚂蚁财富-某基金-卖出至余额宝", "20"),
                // 转换 15:00 后：与买入同样顺延
                row("2026102300000000000025", "2026/10/23 20:36",
                        "蚂蚁财富-某基金[转换至]另一只基金", "20"));

        assertThat(byNo.get("2026102300000000000022").getConfirmDate()).isEqualTo(monday);
        assertThat(byNo.get("2026102300000000000023").getConfirmDate()).isEqualTo(friday);
        assertThat(byNo.get("2026102300000000000024").getConfirmDate()).isEqualTo(friday);
        assertThat(byNo.get("2026102300000000000025").getConfirmDate()).isEqualTo(monday);
    }

    /**
     * 余额宝收益发放与转账不是基金申赎，必须被排除
     */
    @Test
    @DisplayName("余额宝收益发放不算基金动作")
    void skipYuEbao() {
        List<AlipayBillRow> rows = parser.parse(bytesOf(HEAD + HEADER
                + row("2026011500000000000031", "2026/11/1 2:10", "余额宝-2026.10.31-收益发放", "0.01")
                + "\n" + row("2026011500000000000032", "2026/10/25 12:13", "转账收款到余额宝", "58.33")));

        // 先确认解析本身没丢行，再确认分类把它们排除了，否则用例会因为「解析出 0 行」而假通过
        assertThat(rows).hasSize(2);
        assertThat(classifier.classify(rows)).isEmpty();
    }

    /**
     * 找不到表头时要给出能看懂的原因，而不是抛空指针
     */
    @Test
    @DisplayName("非账单文件给出可读报错")
    void rejectNonBill() {
        assertThatThrownBy(() -> parser.parse(bytesOf("随便一段文字")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode()));
    }

    /**
     * 拼一行官方格式的记录，列序与表头一一对应
     *
     * @param tradeNo 交易号，须为十位以上纯数字
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

    /**
     * 把原始行拼成可解析的账单文本，解析并分类后按交易号索引
     *
     * @param lines 原始行
     * @return 交易号到动作
     */
    private Map<String, AlipayFundOrder> byTradeNo(String... lines) {
        List<AlipayBillRow> rows = parser.parse(bytesOf(HEAD + HEADER + String.join("\n", lines)));
        assertThat(rows).hasSize(lines.length);
        return classifier.classify(rows).stream()
                .collect(Collectors.toMap(AlipayFundOrder::getTradeNo, Function.identity()));
    }

    /**
     * 按 GBK 编码字节，模拟官方导出
     *
     * @param text 文本
     * @return 字节
     */
    private byte[] bytesOf(String text) {
        return text.getBytes(Charset.forName("GBK"));
    }
}
