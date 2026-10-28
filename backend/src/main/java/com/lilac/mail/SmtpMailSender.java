package com.lilac.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import jakarta.mail.Transport;

/**
 * SMTP 发送器，按当前密钥配置即时构建会话，配置改错后下一次发送立刻按新配置生效
 */
@Slf4j
@Component
public class SmtpMailSender {

    private final MailProperties mailProperties;

    /**
     * 注入邮件配置，超时与协议参数都取自它
     *
     * @param mailProperties 邮件配置
     */
    public SmtpMailSender(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    /**
     * 发送一封 HTML + 纯文本双版本邮件，失败时抛出原始异常由调用方留存与留痕
     *
     * @param settings 发送参数，含授权码
     * @param subject  主题
     * @param html     HTML 正文
     * @param text     纯文本正文，客户端不支持 HTML 时读它
     */
    public void send(SmtpSettingsResolver.SmtpSettings settings, String subject, String html, String text) {
        JavaMailSenderImpl sender = buildSender(settings);
        MimeMessage message = sender.createMimeMessage();
        try {
            // multipart 才能一次带出 HTML 与纯文本两版，两者内容同源
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.getFrom());
            helper.setTo(settings.getRecipients().toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("邮件组装失败：" + e.getMessage(), e);
        }
    }

    /**
     * 测试 SMTP 连接与认证，不发送邮件
     *
     * @param settings 当前生效的 SMTP 参数
     */
    public void testConnection(SmtpSettingsResolver.SmtpSettings settings) {
        JavaMailSenderImpl sender = buildSender(settings);
        try {
            Transport transport = sender.getSession().getTransport("smtp");
            try {
                transport.connect(settings.getHost(), settings.getPort(), settings.getUsername(), settings.getPassword());
            } finally {
                transport.close();
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("SMTP 连接或认证失败", e);
        }
    }

    /**
     * 按参数构建邮件发送器，端口 465 用 SSL、其余用 STARTTLS
     *
     * @param settings 发送参数
     * @return 发送器
     */
    private JavaMailSenderImpl buildSender(SmtpSettingsResolver.SmtpSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        sender.setUsername(settings.getUsername());
        sender.setPassword(settings.getPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setJavaMailProperties(propertiesOf(settings.getPort()));
        return sender;
    }

    /**
     * 拼 SMTP 会话属性，超时取值较短以免拖住收盘前时点
     *
     * @param port SMTP 端口
     * @return 会话属性
     */
    private Properties propertiesOf(int port) {
        Properties properties = new Properties();
        boolean ssl = port == 465;
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.timeout", String.valueOf(mailProperties.getTimeoutMillis()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(mailProperties.getTimeoutMillis()));
        properties.put("mail.smtp.writetimeout", String.valueOf(mailProperties.getTimeoutMillis()));
        // 465 直连 TLS，587 必须先升级到 TLS 再认证，否则服务端会以明文拒绝
        properties.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        properties.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
        properties.put("mail.smtp.starttls.required", String.valueOf(!ssl));
        // 部分云主机默认协议过旧，显式钉到 TLS 1.2 以免握手被拒
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        return properties;
    }
}
