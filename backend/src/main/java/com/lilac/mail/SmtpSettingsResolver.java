package com.lilac.mail;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SMTP 运行参数，账号、收件人与授权码均从密钥文件读，配置文件只留非身份项，任何接口都不回显
 */
@Data
@Component
public class SmtpSettingsResolver {

    /**
     * 授权码在密钥文件里的键名
     */
    public static final String PASSWORD_KEY = "SMTP_PASSWORD";

    /**
     * 账号在密钥文件里的键名
     */
    public static final String USERNAME_KEY = "SMTP_USERNAME";

    /**
     * 发件地址在密钥文件里的键名，缺省取账号
     */
    public static final String FROM_KEY = "SMTP_FROM";

    /**
     * 收件地址在密钥文件里的键名，多个用逗号或分号分隔
     */
    public static final String RECIPIENTS_KEY = "SMTP_RECIPIENTS";

    /**
     * SMTP 主机在密钥文件里的键名
     */
    public static final String HOST_KEY = "SMTP_HOST";

    /**
     * SMTP 端口在密钥文件里的键名，缺省用配置文件里的端口
     */
    public static final String PORT_KEY = "SMTP_PORT";

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
     * 合成 SMTP 运行参数，密钥文件优先、配置文件兜底，缺项时给清单而不抛异常
     *
     * @return SMTP 参数
     */
    public SmtpSettings resolve() {
        SecretsLoader.Secrets secrets = secretsLoader.loadAll(mailProperties.getSecretsFile());
        SmtpSettings settings = new SmtpSettings();
        settings.setHost(prefer(secrets.get(HOST_KEY), mailProperties.getHost()));
        settings.setPort(portOf(prefer(secrets.get(PORT_KEY), null)));
        settings.setUsername(prefer(secrets.get(USERNAME_KEY), mailProperties.getUsername()));
        settings.setPassword(secrets.get(PASSWORD_KEY));
        settings.setFrom(prefer(secrets.get(FROM_KEY), prefer(mailProperties.getFrom(), settings.getUsername())));
        settings.setRecipients(recipientsOf(secrets.get(RECIPIENTS_KEY), mailProperties.getRecipients()));
        settings.setMissing(settings.missingItems());
        return settings;
    }

    /**
     * 只判断账号与授权码是否都已配置，供页面显示「已配置 / 未配置」而不回显内容
     *
     * @return 已配置返回 true
     */
    public boolean passwordConfigured() {
        SecretsLoader.Secrets secrets = secretsLoader.loadAll(mailProperties.getSecretsFile());
        return notBlank(secrets.get(PASSWORD_KEY))
                && notBlank(prefer(secrets.get(USERNAME_KEY), mailProperties.getUsername()));
    }

    /**
     * 解析端口，密钥文件写的端口不合法时退回配置文件端口
     *
     * @param fromSecrets 密钥文件里的端口
     * @return 端口
     */
    private int portOf(String fromSecrets) {
        if (notBlank(fromSecrets)) {
            try {
                int port = Integer.parseInt(fromSecrets.trim());
                if (port > 0 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // 端口写错不阻断，退回配置文件里的端口，发送时再暴露真实失败
            }
        }
        return mailProperties.getPort() > 0 ? mailProperties.getPort() : 587;
    }

    /**
     * 解析收件地址，密钥文件优先，多个用逗号或分号分隔
     *
     * @param fromSecrets 密钥文件里的收件地址
     * @param fallback    配置文件里的收件地址
     * @return 收件地址列表
     */
    private List<String> recipientsOf(String fromSecrets, List<String> fallback) {
        List<String> recipients = new ArrayList<>();
        if (notBlank(fromSecrets)) {
            for (String item : fromSecrets.split("[,;\\s]+")) {
                if (notBlank(item)) {
                    recipients.add(item.trim());
                }
            }
            return recipients;
        }
        if (fallback != null) {
            fallback.stream().filter(SmtpSettingsResolver::notBlank).map(String::trim).forEach(recipients::add);
        }
        return recipients;
    }

    /**
     * 取第一个非空白值，为空时返回兜底值
     *
     * @param first  首选值
     * @param second 兜底值
     * @return 取值
     */
    private String prefer(String first, String second) {
        return notBlank(first) ? first.trim() : (notBlank(second) ? second.trim() : null);
    }

    /**
     * 判断字符串非空白
     *
     * @param value 原值
     * @return 非空白返回 true
     */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 合并后的 SMTP 参数，授权码字段不参与任何回显
     */
    @Data
    public static class SmtpSettings {

        /**
         * SMTP 主机
         */
        private String host;

        /**
         * SMTP 端口，465 走 SSL、587 走 STARTTLS
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
                items.add("SMTP 主机未配置，请在 config/secrets.env 写入键 " + HOST_KEY);
            }
            if (username == null || username.isBlank()) {
                items.add("SMTP 账号未配置，请在 config/secrets.env 写入键 " + USERNAME_KEY);
            }
            if (password == null || password.isBlank()) {
                items.add("SMTP 授权码未配置，请在 config/secrets.env 写入键 " + PASSWORD_KEY);
            }
            if (recipients == null || recipients.isEmpty()) {
                items.add("收件地址未配置，请在 config/secrets.env 写入键 " + RECIPIENTS_KEY);
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
