package com.lilac.mail;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SMTP 运行参数，从配置与密钥文件合成，授权码只在本对象里流转，不出接口
 */
@Data
@Component
public class SmtpSettingsResolver {

    /**
     * 授权码在密钥文件里的键名
     */
    public static final String PASSWORD_KEY = "SMTP_PASSWORD";

    private final MailProperties mailProperties;

    private final SecretsLoader secretsLoader;

    /**
     * 注入配置与密钥读取
     *
     * @param mailProperties 邮件配置
     * @param secretsLoader  密钥读取
     */
    public SmtpSettingsResolver(MailProperties mailProperties, SecretsLoader secretsLoader) {
        this.mailProperties = mailProperties;
        this.secretsLoader = secretsLoader;
    }

    /**
     * 合成 SMTP 运行参数，缺项时给出缺失清单而不是抛异常
     *
     * @return SMTP 参数
     */
    public SmtpSettings resolve() {
        SmtpSettings settings = new SmtpSettings();
        settings.setHost(mailProperties.getHost());
        settings.setPort(mailProperties.getPort());
        settings.setUsername(mailProperties.getUsername());
        settings.setPassword(secretsLoader.load(mailProperties.getSecretsFile()).get(PASSWORD_KEY));
        settings.setFrom(mailProperties.getFrom() == null || mailProperties.getFrom().isBlank()
                ? mailProperties.getUsername() : mailProperties.getFrom());
        settings.setRecipients(mailProperties.getRecipients());
        settings.setMissing(settings.missingItems());
        return settings;
    }

    /**
     * 只判断授权码是否已配置，供页面显示「已配置 / 未配置」而不回显内容
     *
     * @return 已配置返回 true
     */
    public boolean passwordConfigured() {
        String password = secretsLoader.load(mailProperties.getSecretsFile()).get(PASSWORD_KEY);
        return password != null && !password.isBlank();
    }

    /**
     * 合并后的 SMTP 参数，授权码字段不参与序列化外的任何回显
     */
    @Data
    public static class SmtpSettings {

        /**
         * SMTP 主机
         */
        private String host;

        /**
         * SMTP 端口
         */
        private int port;

        /**
         * SMTP 账号
         */
        private String username;

        /**
         * 授权码，只在发送时使用，禁止进日志与接口
         */
        private String password;

        /**
         * 发件地址，未单独配置时取账号
         */
        private String from;

        /**
         * 收件地址
         */
        private List<String> recipients = new ArrayList<>();

        /**
         * 缺失项清单，为空表示可发送
         */
        private List<String> missing = new ArrayList<>();

        /**
         * 逐项检查必填，返回缺失说明
         *
         * @return 缺失说明列表
         */
        public List<String> missingItems() {
            List<String> items = new ArrayList<>();
            if (host == null || host.isBlank()) {
                items.add("SMTP 主机未配置");
            }
            if (username == null || username.isBlank()) {
                items.add("SMTP 账号未配置");
            }
            if (password == null || password.isBlank()) {
                items.add("SMTP 授权码未配置，请在 config/secrets.env 写入键 " + PASSWORD_KEY);
            }
            if (recipients == null || recipients.isEmpty()) {
                items.add("收件地址未配置");
            }
            return items;
        }

        /**
         * 是否具备发送条件
         *
         * @return 可发送返回 true
         */
        public boolean complete() {
            return missing == null || missing.isEmpty();
        }
    }
}
