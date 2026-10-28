package com.lilac.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件渲染测试，重点是必须同屏的误差说明、无法预估单独成段与空持仓提示
 */
class MailRendererTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 30);

    private MailRenderer renderer;

    /**
     * 装配渲染器，模型由每个用例自行组装
     */
    @BeforeEach
    void setUp() {
        renderer = new MailRenderer();
    }

    /**
     * HTML 与纯文本都必须带误差说明与数据截止时间，这是 PRD 的硬要求
     */
    @Test
    @DisplayName("正文同时含误差说明与数据截止时间")
    void disclaimerAndCutoffPresent() {
        MailContent content = contentWithOneEstimable();
        String html = renderer.html(content);
        String text = renderer.text(content);

        assertThat(html).contains(MailRenderer.DISCLAIMER).contains("数据截止：<strong>2026-10-30</strong>");
        assertThat(text).contains(MailRenderer.DISCLAIMER).contains("数据截止：2026-10-30");
    }

    /**
     * 无法预估的基金单独成段并带原因，不与可预估的混在一起
     */
    @Test
    @DisplayName("无法预估的基金单独成段并带原因")
    void unavailableSectionSeparated() {
        MailContent content = contentWithOneEstimable();
        MailContent.Item gap = new MailContent.Item();
        gap.setCode("005827");
        gap.setName("易方达蓝筹精选");
        gap.setCategory("混合型");
        gap.setEstimable(false);
        gap.setUnavailableReason("重仓股停牌且无指数可参照");
        content.getUnavailableItems().add(gap);

        String html = renderer.html(content);
        assertThat(html).contains("今日无法预估").contains("重仓股停牌且无指数可参照");
        assertThat(renderer.text(content)).contains("【无法预估】").contains("重仓股停牌且无指数可参照");
    }

    /**
     * 无持仓时发的是提示邮件，不出现任何持仓表格
     */
    @Test
    @DisplayName("无持仓时渲染提示邮件")
    void emptyHoldingsNotice() {
        MailContent content = new MailContent();
        content.setTradeDate(TRADE_DATE);

        assertThat(content.empty()).isTrue();
        assertThat(renderer.html(content)).contains("尚未录入持仓").doesNotContain("持仓预估</h3>");
        assertThat(renderer.text(content)).contains("尚未录入任何持仓");
        assertThat(renderer.subject(content, false)).contains("未配置持仓");
    }

    /**
     * B 档只给区间、C 档只给方向，渲染不得凭空造出点估计
     */
    @Test
    @DisplayName("无点估计时按区间与方向渲染")
    void noFakePointEstimate() {
        MailContent content = new MailContent();
        content.setTradeDate(TRADE_DATE);
        content.setVarietyCount(1);
        MailContent.Item range = new MailContent.Item();
        range.setCode("005827");
        range.setName("易方达蓝筹精选");
        range.setCategory("混合型");
        range.setTier("B");
        range.setConfidence("中");
        range.setIntervalLow(new BigDecimal("-0.90"));
        range.setIntervalHigh(new BigDecimal("0.08"));
        range.setEstimable(true);
        content.getItems().add(range);

        String text = renderer.text(content);
        assertThat(text).contains("区间 -0.90% ~ +0.08%");
        assertThat(text).doesNotContain("null");
    }

    /**
     * 补发邮件的主题带前缀，便于收件时与常规提醒区分
     */
    @Test
    @DisplayName("补发邮件主题带前缀")
    void resendSubjectPrefixed() {
        MailContent content = contentWithOneEstimable();
        assertThat(renderer.subject(content, true)).startsWith("【补发】");
        assertThat(renderer.subject(content, false)).doesNotStartWith("【补发】");
    }

    /**
     * 品种名里的 HTML 特殊字符必须转义，否则会破坏排版甚至注入标签
     */
    @Test
    @DisplayName("品种名里的特殊字符被转义")
    void escapesHtml() {
        MailContent content = new MailContent();
        content.setTradeDate(TRADE_DATE);
        content.setVarietyCount(1);
        MailContent.Item item = new MailContent.Item();
        item.setCode("000001");
        item.setName("<script>alert(1)</script>");
        item.setCategory("股票型");
        item.setTier("A");
        item.setConfidence("高");
        item.setEstimatedChange(new BigDecimal("1.23"));
        item.setEstimable(true);
        content.getItems().add(item);

        assertThat(renderer.html(content)).doesNotContain("<script>alert(1)</script>");
    }

    /**
     * 造一份只有一个可预估基金的模型
     *
     * @return 邮件模型
     */
    private MailContent contentWithOneEstimable() {
        MailContent content = new MailContent();
        content.setTradeDate(TRADE_DATE);
        content.setVarietyCount(1);
        content.setDataCutoffDate(TRADE_DATE);
        content.setQuoteDate(TRADE_DATE);
        content.setDividendSummary(List.of());
        MailContent.Item item = new MailContent.Item();
        item.setCode("000922");
        item.setName("中证红利ETF联接A");
        item.setCategory("红利");
        item.setTier("A");
        item.setConfidence("高");
        item.setBranch("持仓加权");
        item.setEstimatedChange(new BigDecimal("0.52"));
        item.setEstimable(true);
        content.getItems().add(item);
        return content;
    }
}
