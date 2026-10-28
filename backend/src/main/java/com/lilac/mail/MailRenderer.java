package com.lilac.mail;

import cn.hutool.core.util.EscapeUtil;
import com.lilac.domain.vo.DividendAdvisoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件渲染，同一份模型同时产出 HTML 与纯文本，手机端优先读 HTML、客户端不支持时退回纯文本
 */
@Slf4j
@Component
public class MailRenderer {

    /**
     * 同屏必须出现的误差说明，正文与页脚各放一次
     */
    public static final String DISCLAIMER = "预估基于重仓股与指数推算，与实际净值存在误差";

    /**
     * 渲染邮件主题，日期与状态一眼可见
     *
     * @param content 邮件模型
     * @param resend  是否补发
     * @return 邮件主题
     */
    public String subject(MailContent content, boolean resend) {
        String prefix = resend ? "【补发】" : "";
        if (content.empty()) {
            return prefix + "Fund " + content.getTradeDate() + " 未配置持仓，收盘前提醒改发提示";
        }
        return prefix + "Fund " + content.getTradeDate() + " 收盘前提醒（含预估与建议）";
    }

    /**
     * 渲染 HTML 正文，表格与段落均为内联样式，手机端直接可读
     *
     * @param content 邮件模型
     * @return HTML 正文
     */
    public String html(MailContent content) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(escape(subject(content, false))).append("</title></head>")
                .append("<body style=\"margin:0;padding:16px;background:#f5f6f8;")
                .append("font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2329;\">")
                .append("<div style=\"max-width:720px;margin:0 auto;background:#fff;border-radius:8px;padding:16px;\">");
        if (content.empty()) {
            appendEmptyNotice(builder, content);
        } else {
            appendHeader(builder, content);
            appendEstimateTable(builder, content);
            appendUnavailableSection(builder, content);
            appendDividendSection(builder, content);
            appendFooter(builder, content);
        }
        builder.append("</div></body></html>");
        return builder.toString();
    }

    /**
     * 渲染纯文本兜底正文，结构与 HTML 一致，弱网或纯文本客户端也能读全
     *
     * @param content 邮件模型
     * @return 纯文本正文
     */
    public String text(MailContent content) {
        List<String> lines = new ArrayList<>();
        if (content.empty()) {
            lines.add("Fund 收盘前提醒 " + content.getTradeDate());
            lines.add("");
            lines.add("尚未录入任何持仓，今日不发常规提醒。");
            lines.add("请先到页面录入持仓，录入后收盘前会收到含预估与建议的邮件。");
            lines.add("");
            lines.add(DISCLAIMER);
            return String.join(System.lineSeparator(), lines);
        }
        lines.add("Fund 收盘前提醒 " + content.getTradeDate());
        lines.add("数据截止：" + dataCutoff(content) + "    行情日期：" + text(content.getQuoteDate()));
        lines.add("");
        lines.add("【持仓预估】");
        if (content.getItems().isEmpty()) {
            lines.add("  今日无基金可给出预估，原因见下方「无法预估」。");
        }
        for (MailContent.Item item : content.getItems()) {
            lines.add("  " + item.getCode() + " " + item.getName() + "（" + text(item.getCategory()) + "）"
                    + " " + changeText(item) + " 可信度 " + text(item.getConfidence())
                    + " 档位 " + text(item.getTier()));
        }
        if (content.getOverallChange() != null) {
            lines.add("  整体：" + percent(content.getOverallChange())
                    + (content.getOverallNote() == null ? "" : "（" + content.getOverallNote() + "）"));
        }
        lines.add("");
        lines.add("【无法预估】");
        if (content.getUnavailableItems().isEmpty()) {
            lines.add("  无");
        }
        for (MailContent.Item item : content.getUnavailableItems()) {
            lines.add("  " + item.getCode() + " " + item.getName() + "：" + text(item.getUnavailableReason()));
        }
        lines.add("");
        lines.add("【策略提醒】");
        if (content.getDividendSummary().isEmpty()) {
            lines.add("  今日无红利持仓，暂无红利加减仓建议。");
        }
        for (String summary : content.getDividendSummary()) {
            lines.add("  " + summary);
        }
        lines.add("");
        lines.add("【风险说明】");
        lines.add("  " + DISCLAIMER + "，建议金额仅供参考，买卖仍由本人执行。");
        return String.join(System.lineSeparator(), lines);
    }

    /**
     * 渲染「未配置持仓」提示邮件的 HTML
     *
     * @param content 邮件模型
     * @param builder 正文构建器
     */
    private void appendEmptyNotice(StringBuilder builder, MailContent content) {
        builder.append("<h2 style=\"font-size:18px;margin:0 0 8px;\">尚未录入持仓</h2>")
                .append("<p style=\"font-size:14px;line-height:1.7;margin:0 0 8px;\">")
                .append(escape(content.getTradeDate() + " 的常规收盘前提醒未发送，因为没有可提醒的持仓。"))
                .append("</p>")
                .append("<p style=\"font-size:14px;line-height:1.7;margin:0 0 8px;\">")
                .append("请先到页面录入持仓，录入后收盘前会收到含预估涨跌与建议金额的邮件。")
                .append("</p>")
                .append("<p style=\"font-size:12px;color:#8a919f;margin:16px 0 0;\">")
                .append(escape(DISCLAIMER)).append("</p>");
    }

    /**
     * 渲染正文头部，含数据截止时间与误差说明
     *
     * @param builder 正文构建器
     * @param content 邮件模型
     */
    private void appendHeader(StringBuilder builder, MailContent content) {
        builder.append("<h2 style=\"font-size:18px;margin:0 0 8px;\">收盘前提醒 ")
                .append(escape(content.getTradeDate().toString())).append("</h2>")
                .append("<p style=\"font-size:13px;line-height:1.7;margin:0 0 4px;color:#40474f;\">")
                .append("数据截止：<strong>").append(escape(dataCutoff(content))).append("</strong>")
                .append("　行情日期：").append(escape(text(content.getQuoteDate())))
                .append("　股息率日期：").append(escape(text(content.getDividendDataDate())))
                .append("</p>")
                .append("<p style=\"font-size:13px;line-height:1.7;margin:0 0 12px;color:#c0392b;\">")
                .append(escape(DISCLAIMER)).append("。</p>");
    }

    /**
     * 渲染持仓预估表格，手机宽度下按两列压缩
     *
     * @param builder 正文构建器
     * @param content 邮件模型
     */
    private void appendEstimateTable(StringBuilder builder, MailContent content) {
        builder.append("<h3 style=\"font-size:15px;margin:12px 0 6px;\">持仓预估</h3>");
        if (content.getItems().isEmpty()) {
            builder.append("<p style=\"font-size:13px;color:#8a919f;margin:0 0 8px;\">")
                    .append(escape(text(content.getEstimateUnavailableReason())))
                    .append("</p>");
        } else {
            builder.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">")
                    .append("<thead><tr style=\"background:#f0f2f5;\">")
                    .append("<th style=\"text-align:left;padding:6px;\">基金</th>")
                    .append("<th style=\"text-align:left;padding:6px;\">类型 / 档位</th>")
                    .append("<th style=\"text-align:right;padding:6px;\">预估</th>")
                    .append("<th style=\"text-align:right;padding:6px;\">可信度</th>")
                    .append("</tr></thead><tbody>");
            for (MailContent.Item item : content.getItems()) {
                builder.append("<tr style=\"border-bottom:1px solid #eef0f3;\">")
                        .append("<td style=\"padding:6px;\">").append(escape(item.getCode()))
                        .append(" ").append(escape(text(item.getName()))).append("</td>")
                        .append("<td style=\"padding:6px;\">").append(escape(text(item.getCategory())))
                        .append(" / ").append(escape(text(item.getTier()))).append("</td>")
                        .append("<td style=\"padding:6px;text-align:right;\">").append(escape(changeText(item)))
                        .append("</td>")
                        .append("<td style=\"padding:6px;text-align:right;\">").append(escape(text(item.getConfidence())))
                        .append("</td></tr>");
            }
            builder.append("</tbody></table>");
        }
        if (content.getOverallChange() != null) {
            builder.append("<p style=\"font-size:13px;margin:8px 0 0;\">整体预估：<strong>")
                    .append(escape(percent(content.getOverallChange()))).append("</strong>")
                    .append(content.getOverallNote() == null ? "" : "（" + escape(content.getOverallNote()) + "）")
                    .append("</p>");
        }
    }

    /**
     * 渲染无法预估段，单独成段并说明原因，不与正常基金混排
     *
     * @param builder 正文构建器
     * @param content 邮件模型
     */
    private void appendUnavailableSection(StringBuilder builder, MailContent content) {
        builder.append("<h3 style=\"font-size:15px;margin:16px 0 6px;color:#c0392b;\">今日无法预估</h3>");
        if (content.getUnavailableItems().isEmpty()) {
            builder.append("<p style=\"font-size:13px;color:#8a919f;margin:0 0 8px;\">无</p>");
            return;
        }
        builder.append("<ul style=\"font-size:13px;line-height:1.8;margin:0 0 8px;padding-left:18px;\">");
        for (MailContent.Item item : content.getUnavailableItems()) {
            builder.append("<li>").append(escape(item.getCode())).append(" ")
                    .append(escape(text(item.getName()))).append("：")
                    .append(escape(text(item.getUnavailableReason()))).append("</li>");
        }
        builder.append("</ul>");
    }

    /**
     * 渲染红利建议段，参数不可用时明确说明不给动作
     *
     * @param builder 正文构建器
     * @param content 邮件模型
     */
    private void appendDividendSection(StringBuilder builder, MailContent content) {
        builder.append("<h3 style=\"font-size:15px;margin:16px 0 6px;\">策略提醒</h3>");
        if (content.getDividendSummary().isEmpty()) {
            builder.append("<p style=\"font-size:13px;color:#8a919f;margin:0 0 8px;\">今日无红利持仓，暂无红利加减仓建议。</p>");
            return;
        }
        builder.append("<ul style=\"font-size:13px;line-height:1.8;margin:0 0 8px;padding-left:18px;\">");
        for (String summary : content.getDividendSummary()) {
            builder.append("<li>").append(escape(summary)).append("</li>");
        }
        builder.append("</ul>");
        DividendAdvisoryVO advisory = content.getDividendAdvisory();
        if (advisory != null && advisory.getTotalAddAmount() != null && advisory.getTotalReduceAmount() != null) {
            builder.append("<p style=\"font-size:13px;margin:0 0 8px;\">买入合计 ")
                    .append(escape(money(advisory.getTotalAddAmount()))).append("　卖出合计 ")
                    .append(escape(money(advisory.getTotalReduceAmount()))).append("</p>");
        }
    }

    /**
     * 渲染页脚，把误差与执行口径再写一次
     *
     * @param builder 正文构建器
     * @param content 邮件模型
     */
    private void appendFooter(StringBuilder builder, MailContent content) {
        builder.append("<hr style=\"border:none;border-top:1px solid #eef0f3;margin:16px 0 8px;\">")
                .append("<p style=\"font-size:12px;color:#8a919f;line-height:1.7;margin:0;\">")
                .append(escape(DISCLAIMER)).append("；建议金额仅供参考，买卖仍由本人在支付宝或券商执行。")
                .append(" 数据截止：").append(escape(dataCutoff(content))).append("</p>");
    }

    /**
     * 取数据截止文字，缺失时退回交易日
     *
     * @param content 邮件模型
     * @return 截止文字
     */
    private String dataCutoff(MailContent content) {
        if (content.getDataCutoffDate() != null) {
            return content.getDataCutoffDate().toString();
        }
        return content.getTradeDate() == null ? "—" : content.getTradeDate().toString();
    }

    /**
     * 拼单只基金的涨跌文字，B 档给区间、C 档给方向，绝不编数
     *
     * @param item 邮件行
     * @return 涨跌文字
     */
    private String changeText(MailContent.Item item) {
        if (item.getEstimatedChange() != null) {
            return percent(item.getEstimatedChange());
        }
        if (item.getIntervalLow() != null && item.getIntervalHigh() != null) {
            return "区间 " + percent(item.getIntervalLow()) + " ~ " + percent(item.getIntervalHigh());
        }
        if (item.getDirection() != null) {
            return "方向 " + item.getDirection();
        }
        return "—";
    }

    /**
     * 百分比格式化，带符号并保留两位
     *
     * @param value 百分点
     * @return 文本
     */
    private String percent(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString() + "%";
    }

    /**
     * 金额格式化，保留两位
     *
     * @param value 金额
     * @return 文本
     */
    private String money(BigDecimal value) {
        return value == null ? "—" : value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元";
    }

    /**
     * 空值替换为横线，避免正文出现 null
     *
     * @param value 原文
     * @return 文本
     */
    private String text(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    /**
     * HTML 转义，品种名里的特殊字符不能破坏排版
     *
     * @param value 原文
     * @return 转义后文本
     */
    private String escape(String value) {
        return EscapeUtil.escapeHtml4(value == null ? "" : value);
    }
}
