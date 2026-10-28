package com.lilac.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SMTP 参数合成测试，重点是密钥文件优先、缺项清单与「授权码与账号都不回显」
 */
class SmtpSettingsResolverTest {

    @TempDir
    Path tempDir;

    private MailProperties mailProperties;
    private SmtpSettingsResolver resolver;

    /**
     * 配置只给端口，账号与收件人都从密钥文件来
     */
    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setPort(587);
        mailProperties.setSecretsFile(tempDir.resolve("secrets.env").toString());
        resolver = new SmtpSettingsResolver(mailProperties, new SecretsLoader());
    }

    /**
     * 密钥文件写全时视为可发送，发件地址缺省取账号，端口以密钥文件为准
     */
    @Test
    @DisplayName("密钥文件齐全时视为可发送")
    void completeWhenSecretsPresent() throws IOException {
        writeSecrets(String.join(System.lineSeparator(),
                "SMTP_HOST=smtp.qq.com",
                "SMTP_PORT=587",
                "SMTP_USERNAME=sender@example.com",
                "SMTP_RECIPIENTS=me@example.com",
                "SMTP_PASSWORD=token123"));

        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();
        assertThat(settings.complete()).isTrue();
        assertThat(settings.getHost()).isEqualTo("smtp.qq.com");
        assertThat(settings.getPort()).isEqualTo(587);
        assertThat(settings.getFrom()).isEqualTo("sender@example.com");
        assertThat(settings.getRecipients()).containsExactly("me@example.com");
        assertThat(resolver.passwordConfigured()).isTrue();
    }

    /**
     * 密钥文件优先于配置文件里的兜底值，改密钥文件不用改 yml
     */
    @Test
    @DisplayName("密钥文件优先于配置文件兜底值")
    void secretsOverrideProperties() throws IOException {
        mailProperties.setHost("smtp.old.example.com");
        mailProperties.setUsername("old@example.com");
        mailProperties.setRecipients(List.of("old-recipient@example.com"));
        writeSecrets(String.join(System.lineSeparator(),
                "SMTP_HOST=smtp.new.example.com",
                "SMTP_USERNAME=new@example.com",
                "SMTP_RECIPIENTS=new-recipient@example.com",
                "SMTP_PASSWORD=token123"));

        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();
        assertThat(settings.getHost()).isEqualTo("smtp.new.example.com");
        assertThat(settings.getUsername()).isEqualTo("new@example.com");
        assertThat(settings.getRecipients()).containsExactly("new-recipient@example.com");
    }

    /**
     * 缺项一次性列全，且说明里不出现授权码内容
     */
    @Test
    @DisplayName("缺项一次列全且不含授权码内容")
    void missingItemsListedWithoutSecretValue() throws IOException {
        writeSecrets("SMTP_PASSWORD=token123");
        mailProperties.setHost(null);
        mailProperties.setUsername(null);

        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();
        assertThat(settings.complete()).isFalse();
        assertThat(settings.getMissing()).hasSize(3);
        assertThat(String.join("|", settings.getMissing()))
                .contains("SMTP_HOST").contains("SMTP_USERNAME").contains("SMTP_RECIPIENTS")
                .doesNotContain("token123");
        assertThat(resolver.passwordConfigured()).isFalse();
    }

    /**
     * 密钥文件里端口写错时退回配置文件端口，不因一行笔误阻断发送
     */
    @Test
    @DisplayName("端口非法时退回配置端口")
    void invalidPortFallsBack() throws IOException {
        writeSecrets(String.join(System.lineSeparator(),
                "SMTP_HOST=smtp.qq.com",
                "SMTP_PORT=abc",
                "SMTP_USERNAME=sender@example.com",
                "SMTP_RECIPIENTS=me@example.com",
                "SMTP_PASSWORD=token123"));
        assertThat(resolver.resolve().getPort()).isEqualTo(587);
    }

    /**
     * 收件人支持逗号、分号与空白分隔，便于手写多个地址
     */
    @Test
    @DisplayName("多个收件人按分隔符拆分")
    void splitsMultipleRecipients() throws IOException {
        writeSecrets(String.join(System.lineSeparator(),
                "SMTP_HOST=smtp.qq.com",
                "SMTP_USERNAME=sender@example.com",
                "SMTP_RECIPIENTS=a@example.com; b@example.com,c@example.com",
                "SMTP_PASSWORD=token123"));

        assertThat(resolver.resolve().getRecipients())
                .containsExactly("a@example.com", "b@example.com", "c@example.com");
    }

    /**
     * 写密钥文件
     *
     * @param content 文件内容
     * @throws IOException 写入失败
     */
    private void writeSecrets(String content) throws IOException {
        Files.writeString(Path.of(mailProperties.getSecretsFile()), content, StandardCharsets.UTF_8);
    }
}
