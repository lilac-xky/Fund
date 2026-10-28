package com.lilac.mail;

import com.lilac.domain.dto.estimate.EstimateBatchRequest;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.service.EstimateService;
import com.lilac.task.CloseReminderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 收盘前提醒邮件的实现，组装内容、发送 SMTP、失败留存并按需带出补发段
 */
@Slf4j
@Component
public class CloseReminderMailService implements CloseReminderPort {

    private static final int RESEND_LIMIT = 5;

    private final MailProperties mailProperties;

    private final MailContentBuilder contentBuilder;

    private final MailRenderer renderer;

    private final SmtpMailSender mailSender;

    private final SmtpSettingsResolver settingsResolver;

    private final MailArchive archive;

    private final EstimateService estimateService;

    /**
     * 注入邮件链路的全部环节，任何一个环节失败都按发送失败处理
     *
     * @param mailProperties   邮件配置
     * @param contentBuilder   内容装配
     * @param renderer         渲染
     * @param mailSender       SMTP 发送
     * @param settingsResolver SMTP 参数解析
     * @param archive          失败留存
     * @param estimateService  预估业务
     */
    public CloseReminderMailService(MailProperties mailProperties, MailContentBuilder contentBuilder,
                                    MailRenderer renderer, SmtpMailSender mailSender,
                                    SmtpSettingsResolver settingsResolver, MailArchive archive,
                                    EstimateService estimateService) {
        this.mailProperties = mailProperties;
        this.contentBuilder = contentBuilder;
        this.renderer = renderer;
        this.mailSender = mailSender;
        this.settingsResolver = settingsResolver;
        this.archive = archive;
        this.estimateService = estimateService;
    }

    /**
     * 产出并发送收盘前提醒，失败时留存正文后抛出，由编排层记失败留痕
     *
     * @param tradeDate 交易日
     * @param resend    是否携带此前未送达内容的补发
     * @return 发送成功的正文留存路径，留存失败为空
     */
    @Override
    public String send(LocalDate tradeDate, boolean resend) {
        if (!mailProperties.isEnabled()) {
            throw new com.lilac.task.ReminderDeliveryException("邮件开关已关闭（fund.mail.enabled=false）", null);
        }
        SmtpSettingsResolver.SmtpSettings settings = settingsResolver.resolve();
        MailContent content = contentBuilder.build(tradeDate, estimateOf());
        List<MailArchive.Pending> pending = resend ? archive.pendingResend(tradeDate, RESEND_LIMIT) : List.of();
        // 只有真的带上了未送达内容才自称补发，否则主题会出现没有实际补发内容的「【补发】」
        boolean withResend = !pending.isEmpty();
        String subject = renderer.subject(content, withResend);
        String html = renderer.html(content);
        String text = renderer.text(content);
        if (withResend) {
            List<String> summaries = pending.stream().map(MailArchive.Pending::getSummary).toList();
            text = text + System.lineSeparator() + resendText(summaries);
            html = html.replace("</div></body></html>", resendHtml(summaries) + "</div></body></html>");
        }
        try {
            if (!settings.complete()) {
                throw new IllegalStateException("SMTP 参数不完整：" + String.join("；", settings.getMissing()));
            }
            mailSender.send(settings, subject, html, text);
            // 发出去之后才标记，发送失败时这些失败件要留到下次继续补
            archive.markResent(pending);
            log.info("收盘前提醒已发送 {} 收件人 {} 个 补发 {} 封", tradeDate, settings.getRecipients().size(),
                    pending.size());
        } catch (RuntimeException e) {
            // 发不出去时先把内容落盘，页面上才有东西可看、下次才有东西可补发
            String path = archive.save(tradeDate, "failed", subject, html, text);
            log.warn("收盘前提醒发送失败 {}：{}，内容留存 {}", tradeDate, e.getMessage(), path);
            throw new com.lilac.task.ReminderDeliveryException("邮件未送达：" + reason(e), path);
        }
        return archive.save(tradeDate, "sent", subject, html, text);
    }

    /**
     * 判断邮件通道是否就绪，供页面提示「已配置 / 未配置」而不回显授权码
     *
     * @return 就绪返回 true
     */
    public boolean ready() {
        if (!mailProperties.isEnabled()) {
            return false;
        }
        return settingsResolver.resolve().complete();
    }

    /**
     * 邮件通道状态说明，只描述缺什么，不出现授权码内容
     *
     * @return 说明文字
     */
    public String statusNote() {
        if (!mailProperties.isEnabled()) {
            return "邮件开关已关闭";
        }
        SmtpSettingsResolver.SmtpSettings settings = settingsResolver.resolve();
        return settings.complete() ? "邮件通道已就绪" : String.join("；", settings.getMissing());
    }

    /**
     * 取当日预估，失败时不阻断邮件，只是把「无法预估」写进正文
     *
     * @return 预估总览，失败返回 null
     */
    private EstimateBatchVO estimateOf() {
        try {
            return estimateService.estimateBatch(new EstimateBatchRequest());
        } catch (RuntimeException e) {
            log.warn("邮件取预估失败，正文按无法预估处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 拼纯文本版补发段
     *
     * @param pending 未送达摘要
     * @return 补发段
     */
    private String resendText(List<String> pending) {
        StringBuilder builder = new StringBuilder(System.lineSeparator()).append("【此前未送达的提醒】");
        builder.append(System.lineSeparator());
        for (String item : pending) {
            builder.append("  ").append(item).append(System.lineSeparator());
        }
        builder.append("  以上为上次发送失败的提醒，正文已留存在服务器 data/mail 目录。");
        return builder.toString();
    }

    /**
     * 拼 HTML 版补发段
     *
     * @param pending 未送达摘要
     * @return 补发段
     */
    private String resendHtml(List<String> pending) {
        StringBuilder builder = new StringBuilder("<h3 style=\"font-size:15px;margin:16px 0 6px;\">此前未送达的提醒</h3>")
                .append("<ul style=\"font-size:13px;line-height:1.8;margin:0 0 8px;padding-left:18px;\">");
        for (String item : pending) {
            builder.append("<li>").append(item).append("</li>");
        }
        builder.append("</ul><p style=\"font-size:12px;color:#8a919f;margin:0;\">")
                .append("以上为上次发送失败的提醒，正文已留存在服务器 data/mail 目录。</p>");
        return builder.toString();
    }

    /**
     * 取异常的可读原因，空消息时退回异常类型名
     *
     * @param e 异常
     * @return 原因
     */
    private String reason(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
