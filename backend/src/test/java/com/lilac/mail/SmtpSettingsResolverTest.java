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
 * SMTP 参数合成测试，重点是缺项清单与「授权码不回显」
 */
class SmtpSettingsResolverTest {

    @TempDir
    Path tempDir;

    private MailProperties mailProperties;
    private SmtpSettingsResolver resolver;

    /**
     * 装配一份完整配置，各用例按需要删项
     */
    @BeforeEach
    void setUp() {
        mailProperties = new MailProperties();
        mailProperties.setHost("smtp.qq.com");
        mailProperties.setPort(465);
        mailProperties.setUsername("bot@qq.com");
        mailProperties.setRecipients(List.of("me@qq.com"));
        mailProperties.setSecretsFile(tempDir.resolve("secrets.env").toString());
        resolver = new SmtpSettingsResolver(mailProperties, new SecretsLoader());
    }

    /**
     * 配置齐全且授权码写入密钥文件时视为可发送，发件地址缺省取账号
     */
    @Test
    @DisplayName("配置齐全时视为可发送")
    void completeWhenAllPresent() throws IOException {
        writeSecrets("SMTP_PASSWORD=token123");
        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();

        assertThat(settings.complete()).isTrue();
        assertThat(settings.getMissing()).isEmpty();
        assertThat(settings.getFrom()).isEqualTo("bot@qq.com");
        assertThat(resolver.passwordConfigured()).isTrue();
    }

    /**
     * 授权码缺失时给出「未配置」结论，并且缺失说明里不含任何授权码内容
     */
    @Test
    @DisplayName("授权码缺失时说明不含授权码内容")
    void missingPasswordExplainedWithoutValue() throws IOException {
        writeSecrets("# 空的密钥文件");
        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();

        assertThat(settings.complete()).isFalse();
        assertThat(settings.getMissing()).anySatisfy(item -> assertThat(item).contains("授权码"));
        assertThat(String.join("|", settings.getMissing())).doesNotContain("token123");
        assertThat(resolver.passwordConfigured()).isFalse();
    }

    /**
     * 主机、账号、收件人任一缺失都进缺失清单，一次报全而不是逐个报
     */
    @Test
    @DisplayName("多项缺失一次性列出")
    void listsAllMissingItems() throws IOException {
        writeSecrets("SMTP_PASSWORD=token123");
        mailProperties.setHost("");
        mailProperties.setUsername("");
        mailProperties.setRecipients(List.of());

        SmtpSettingsResolver.SmtpSettings settings = resolver.resolve();
        assertThat(settings.getMissing()).hasSize(3);
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
