package com.lilac.adapter.fund;

import com.lilac.adapter.http.TextFetcher;
import com.lilac.adapter.model.FundHoldingItem;
import com.lilac.adapter.model.FundHoldingSnapshot;
import com.lilac.adapter.model.HoldingKind;
import com.lilac.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 持仓解析测试，用真实返回片段验证列定位、季报与中报的覆盖差异
 */
class FundHoldingAdapterTest {

    private static final String QUARTER_BOX = "<div class='boxitem w790'><h4 class='t'><label class='left'>"
            + "<a title='大成中证红利指数A'>大成中证红利指数A</a>&nbsp;&nbsp;2026年3季度股票投资明细</label>"
            + "<label class='right lab2 xq505'>&nbsp;&nbsp;来源：天天基金&nbsp;&nbsp;截止至："
            + "<font class='px12'>2026-09-30</font></label></h4><div class='space0'></div>"
            + "<table class='w782 comm tzxq'><thead><tr><th>序号</th><th>股票代码</th><th>股票名称</th>"
            + "<th>最新价</th><th>涨跌幅</th><th>相关资讯</th><th>占净值<br />比例</th>"
            + "<th>持股数<br />（万股）</th><th>持仓市值<br />（万元）</th></tr></thead><tbody>"
            + "<tr><td>1</td><td><a href='x'>601919</a></td><td><a href='x'>中远海控</a></td><td></td><td></td>"
            + "<td>变动详情股吧行情</td><td class='tor'>3.03%</td><td>497.23</td><td>8,139.70</td></tr>"
            + "<tr><td>2</td><td><a href='x'>600256</a></td><td><a href='x'>广汇能源</a></td><td></td><td></td>"
            + "<td>变动详情股吧行情</td><td class='tor'>2.01%</td><td>100.00</td><td>1,000.00</td></tr>"
            + "</tbody></table><div class='hide' id='gpdmList'>1.601919,1.600256,</div>"
            + "<div class='tfoot'><font><a>收起持仓明细&gt;&gt;</a></font></div></div>";

    private static final String FULL_BOX = "<div class='boxitem w790'><h4 class='t'><label class='left'>"
            + "<a title='大成中证红利指数A'>大成中证红利指数A</a>&nbsp;&nbsp;2026年2季度股票投资明细</label>"
            + "<label class='right lab2 xq505'>&nbsp;&nbsp;来源：天天基金&nbsp;&nbsp;截止至："
            + "<font class='px12'>2026-06-30</font></label></h4><div class='space0'></div>"
            + "<table class='w782 comm tzxq'><thead><tr><th>序号</th><th>股票代码</th><th>股票名称</th>"
            + "<th>相关资讯</th><th>占净值<br />比例</th><th>持股数</th><th>持仓市值</th></tr></thead><tbody>"
            + "<tr><td>1</td><td>601919</td><td>中远海控</td><td>股吧行情</td><td class='tor'>2.68%</td>"
            + "<td>477.49</td><td>6,331.56</td></tr>"
            + "<tr><td>2</td><td>600256</td><td>广汇能源</td><td>股吧行情</td><td class='tor'>1.50%</td>"
            + "<td>100.00</td><td>1,000.00</td></tr>"
            + "</tbody></table><div class='tfoot'><font><a>显示全部持仓明细&gt;&gt;</a></font></div></div>";

    private static final String APIDATA_PREFIX = "var apidata={ content:\"";
    private static final String APIDATA_SUFFIX = "\",arryear:[2026],curyear:2026};";

    /**
     * 季报表头比中报多两列，按列名定位后仍能取到正确权重
     */
    @Test
    @DisplayName("季报表头多两列仍按列名取到权重")
    void parseQuarterBoxWithExtraColumns() {
        FundHoldingAdapter adapter = newAdapter("");

        List<FundHoldingSnapshot> snapshots = adapter.parseAll("090010", HoldingKind.STOCK, wrap(QUARTER_BOX));

        assertThat(snapshots).hasSize(1);
        FundHoldingSnapshot snapshot = snapshots.get(0);
        assertThat(snapshot.getPeriod()).isEqualTo("2026Q3");
        assertThat(snapshot.getCutoffDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(snapshot.getItems()).hasSize(2);
        assertThat(snapshot.getItems().get(0).getCode()).isEqualTo("601919");
        assertThat(snapshot.getItems().get(0).getName()).isEqualTo("中远海控");
        assertThat(snapshot.getItems().get(0).getWeight()).isEqualByComparingTo("3.03");
        assertThat(snapshot.totalWeight()).isEqualByComparingTo(new BigDecimal("5.04"));
    }

    /**
     * 同一响应里多期快照按截止日升序，季报与中报的覆盖差异保留原样
     */
    @Test
    @DisplayName("多期快照按截止日升序且覆盖差异保留")
    void multiplePeriodsSortedByCutoff() {
        FundHoldingAdapter adapter = newAdapter("");

        List<FundHoldingSnapshot> snapshots =
                adapter.parseAll("090010", HoldingKind.STOCK, wrap(QUARTER_BOX + FULL_BOX));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getPeriod()).isEqualTo("2026Q2");
        assertThat(snapshots.get(1).getPeriod()).isEqualTo("2026Q3");
        assertThat(snapshots.get(0).totalWeight()).isEqualByComparingTo(new BigDecimal("4.18"));
    }

    /**
     * 债券明细的列名是「债券代码」，与股票走同一套按列名定位
     */
    @Test
    @DisplayName("债券明细按列名解析")
    void parseBondBox() {
        String bondBox = QUARTER_BOX.replace("股票投资明细", "债券投资明细")
                .replace("股票代码", "债券代码").replace("股票名称", "债券名称")
                .replace("601919", "232680012").replace("中远海控", "26邮储银行二级资本债01BC");
        FundHoldingAdapter adapter = newAdapter("");

        List<FundHoldingSnapshot> snapshots = adapter.parseAll("000032", HoldingKind.BOND, wrap(bondBox));

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getKind()).isEqualTo(HoldingKind.BOND);
        assertThat(snapshots.get(0).getItems().get(0).getCode()).isEqualTo("232680012");
        assertThat(snapshots.get(0).getItems().get(0).getWeight()).isEqualByComparingTo("3.03");
    }

    /**
     * 空 content 表示该基金确无此类持仓，返回空列表而不是异常
     */
    @Test
    @DisplayName("空 content 返回空列表")
    void emptyContentReturnsEmptyList() {
        FundHoldingAdapter adapter = newAdapter("");

        assertThat(adapter.parseAll("000198", HoldingKind.STOCK,
                "var apidata={ content:\"\",arryear:[],curyear:2026};")).isEmpty();
    }

    /**
     * 返回页面、缺表头列、缺报告期时抛具名异常
     */
    @Test
    @DisplayName("结构变化抛具名异常")
    void structureChangeThrows() {
        FundHoldingAdapter adapter = newAdapter("");

        assertThatThrownBy(() -> adapter.parseAll("090010", HoldingKind.STOCK, "<html>验证页</html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返回页面而非持仓片段");
        assertThatThrownBy(() -> adapter.parseAll("090010", HoldingKind.STOCK, wrap("<div class='boxitem w790'>"
                + "<h4>无报告期</h4><table><tr><td>占净值比例</td></tr></table></div>")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报告期或截止日缺失");
        assertThatThrownBy(() -> adapter.parseAll("090010", HoldingKind.STOCK, wrap("<div class='boxitem w790'>"
                + "<h4>2026年3季度股票投资明细</h4><label>截止至：<font>2026-09-30</font></label>"
                + "<table><tr><td>序号</td></tr><tr><td>1</td></tr></table></div>")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少代码或占净值比例列");
    }

    /**
     * 构造带 apidata 前缀的完整响应片段
     *
     * @param boxes 区块 HTML
     * @return 完整响应
     */
    private String wrap(String boxes) {
        return APIDATA_PREFIX + boxes + APIDATA_SUFFIX;
    }

    /**
     * 构造使用固定文本的适配器
     *
     * @param text 固定返回文本
     * @return 适配器
     */
    private FundHoldingAdapter newAdapter(String text) {
        return new FundHoldingAdapter(new StubFetcher(text), "https://example.com/{kind}/{code}/{year}/{month}",
                "https://fundf10.eastmoney.com/");
    }

    /**
     * 固定返回文本的取数桩
     */
    private record StubFetcher(String text) implements TextFetcher {

        @Override
        public String getText(String url) {
            return text;
        }

        @Override
        public String getText(String url, Charset charset) {
            return text;
        }
    }
}
