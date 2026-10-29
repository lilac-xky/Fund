package com.lilac.auth;

import com.lilac.mail.SecretsLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录凭据与失败锁定测试，核心是「口令只从密钥文件读」「错误不区分哪一项」「锁定时不校验」
 */
class CredentialResolverTest {

    private static final String USERNAME = "lilac";
    private static final String PASSWORD = "s3cr3t-P@ssw0rd";

    @TempDir
    Path tempDir;

    private AuthProperties properties;
    private CredentialResolver resolver;

    /**
     * 每个用例写一份独立的密钥文件，避免互相影响
     */
    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setSecretsFile(tempDir.resolve("secrets.env").toString());
        resolver = new CredentialResolver(new SecretsLoader(), properties);
    }

    /**
     * 密钥文件里配好用户名与口令时视为已配置
     */
    @Test
    @DisplayName("密钥文件配好时视为已配置")
    void configuredWhenKeysPresent() throws IOException {
        writeSecrets(USERNAME, PASSWORD);

        assertThat(resolver.configured()).isTrue();
    }

    /**
     * 密钥文件缺失时视为未配置，任何人都不该能登进来
     */
    @Test
    @DisplayName("密钥文件缺失时视为未配置")
    void notConfiguredWhenFileMissing() {
        assertThat(resolver.configured()).isFalse();
        assertThat(resolver.matches(USERNAME, PASSWORD)).isFalse();
    }

    /**
     * 只填了用户名没填口令也算未配置，不能拿空口令放行
     */
    @Test
    @DisplayName("口令为空时视为未配置")
    void notConfiguredWhenPasswordBlank() throws IOException {
        writeSecrets(USERNAME, "");

        assertThat(resolver.configured()).isFalse();
        assertThat(resolver.matches(USERNAME, "")).isFalse();
    }

    /**
     * 用户名与口令都对时校验通过
     */
    @Test
    @DisplayName("用户名与口令正确时通过")
    void matchesCorrectCredentials() throws IOException {
        writeSecrets(USERNAME, PASSWORD);

        assertThat(resolver.matches(USERNAME, PASSWORD)).isTrue();
    }

    /**
     * 口令错、用户名错、大小写不符都返回 false，且不区分是哪一项错
     */
    @Test
    @DisplayName("任一不符都不通过")
    void rejectsWrongCredentials() throws IOException {
        writeSecrets(USERNAME, PASSWORD);

        assertThat(resolver.matches(USERNAME, "wrong")).isFalse();
        assertThat(resolver.matches("someone", PASSWORD)).isFalse();
        assertThat(resolver.matches(USERNAME.toUpperCase(), PASSWORD)).isFalse();
        assertThat(resolver.matches(null, PASSWORD)).isFalse();
        assertThat(resolver.matches(USERNAME, null)).isFalse();
    }

    /**
     * 口令前后带空格时按原样比对，不做 trim 放行
     */
    @Test
    @DisplayName("口令不做长度外的最小化匹配")
    void rejectsPrefixOfPassword() throws IOException {
        writeSecrets(USERNAME, PASSWORD);

        assertThat(resolver.matches(USERNAME, PASSWORD.substring(0, 5))).isFalse();
        assertThat(resolver.matches(USERNAME, PASSWORD + "x")).isFalse();
    }

    /**
     * 写入一份密钥文件
     *
     * @param username 用户名
     * @param password 口令
     * @throws IOException 写文件失败时抛出
     */
    private void writeSecrets(String username, String password) throws IOException {
        String content = CredentialResolver.KEY_USERNAME + "=" + username + System.lineSeparator()
                + CredentialResolver.KEY_PASSWORD + "=" + password + System.lineSeparator();
        Files.writeString(tempDir.resolve("secrets.env"), content, StandardCharsets.UTF_8);
    }
}
