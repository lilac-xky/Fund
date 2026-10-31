package com.lilac.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密钥读取与留存测试，重点是授权码不被带出去、留存文件能按日期读出
 */
class SecretsAndArchiveTest {

    @TempDir
    Path tempDir;

    private SecretsLoader secretsLoader;

    /**
     * 装配密钥读取
     */
    @BeforeEach
    void setUp() {
        secretsLoader = new SecretsLoader();
    }

    /**
     * 密钥文件按 KEY=VALUE 解析，注释与空行跳过，值两侧引号去掉
     */
    @Test
    @DisplayName("密钥文件解析键值并去引号")
    void parsesSecretsFile() throws IOException {
        Path file = tempDir.resolve("secrets.env");
        Files.writeString(file, String.join(System.lineSeparator(),
                "# 注释行应被跳过",
                "",
                "SMTP_PASSWORD=\"abc123\"",
                "SMTP_USERNAME=sender@example.com",
                "SMTP_RECIPIENTS=a@example.com,b@example.com"), StandardCharsets.UTF_8);

        SecretsLoader.Secrets secrets = secretsLoader.loadAll(file.toString());
        assertThat(secrets.get("SMTP_PASSWORD")).isEqualTo("abc123");
        assertThat(secrets.get("SMTP_USERNAME")).isEqualTo("sender@example.com");
        assertThat(secrets.get("SMTP_RECIPIENTS")).contains("a@example.com");
        assertThat(secrets.get("# 注释行应被跳过")).isNull();
    }

    /**
     * 文件不存在时返回空表而不是抛异常，页面据空表显示「未配置」
     */
    @Test
    @DisplayName("密钥文件缺失时返回空表")
    void missingFileReturnsEmpty() {
        assertThat(secretsLoader.loadAll(tempDir.resolve("nope.env").toString()).isEmpty()).isTrue();
        assertThat(secretsLoader.loadAll(null).isEmpty()).isTrue();
    }

    /**
     * 带 UTF-8 BOM 的密钥文件首个键名必须仍能取到，否则整份配置会静默失效
     */
    @Test
    @DisplayName("带 BOM 的密钥文件首个键仍可读")
    void parsesFileWithBom() throws IOException {
        Path file = tempDir.resolve("bom.env");
        // 记事本与 Windows PowerShell 的 utf8 编码都会写出 BOM
        String content = "AUTH_USERNAME=lilac" + System.lineSeparator()
                + "AUTH_PASSWORD=s3cr3t" + System.lineSeparator();
        Files.write(file, ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8));

        SecretsLoader.Secrets secrets = secretsLoader.loadAll(file.toString());

        assertThat(secrets.get("AUTH_USERNAME")).isEqualTo("lilac");
        assertThat(secrets.get("AUTH_PASSWORD")).isEqualTo("s3cr3t");
    }

    /**
     * 留存能把主题与正文写到 data/mail，失败留存按日期读出，成功留存不进补发清单
     */
    @Test
    @DisplayName("失败留存可读出，成功留存不进补发清单")
    void archiveWritesAndReads() {
        MailArchive archive = archiveAt(tempDir);
        String path = archive.save(LocalDate.of(2026, 10, 29), "failed", "收盘前提醒 2026-10-29",
                "<p>html</p>", "text 正文");
        archive.save(LocalDate.of(2026, 10, 29), "sent", "成功的那封", "<p>html</p>", "text 正文");

        assertThat(path).startsWith("mail/").endsWith(".txt");
        List<MailArchive.Pending> pending = archive.pendingResend(LocalDate.of(2026, 10, 30), 5);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getSummary()).contains("2026-10-29").contains("收盘前提醒 2026-10-29");
    }

    /**
     * 当天失败、当天改好口令再发，必须能补发：这是 ISSUE-012 的验收路径
     */
    @Test
    @DisplayName("当天的失败也算补发项")
    void todayFailureIsPending() {
        MailArchive archive = archiveAt(tempDir);
        archive.save(LocalDate.of(2026, 10, 30), "failed", "今天失败", "<p></p>", "text");
        assertThat(archive.pendingResend(LocalDate.of(2026, 10, 30), 5)).hasSize(1);
    }

    /**
     * 补发成功后该失败件被标记，下次不再重复补发，避免同一封邮件天天附一遍
     */
    @Test
    @DisplayName("已补发的失败件不再重复列出")
    void resentFailureNotListedAgain() {
        MailArchive archive = archiveAt(tempDir);
        archive.save(LocalDate.of(2026, 10, 30), "failed", "今天失败", "<p></p>", "text");
        List<MailArchive.Pending> first = archive.pendingResend(LocalDate.of(2026, 10, 30), 5);
        assertThat(archive.markResent(first)).isTrue();

        assertThat(archive.pendingResend(LocalDate.of(2026, 10, 30), 5)).isEmpty();
    }

    /**
     * 晚于交易日的留存不进本次补发，避免把未来日期的文件算进来
     */
    @Test
    @DisplayName("晚于交易日的留存不进补发清单")
    void futureArchiveExcluded() {
        MailArchive archive = archiveAt(tempDir);
        archive.save(LocalDate.of(2026, 10, 31), "failed", "明天失败", "<p></p>", "text");
        assertThat(archive.pendingResend(LocalDate.of(2026, 10, 30), 5)).isEmpty();
    }

    /**
     * 造一个把 data 根指向临时目录的留存对象
     *
     * @param root 临时根目录
     * @return 留存对象
     */
    private MailArchive archiveAt(Path root) {
        MailArchive archive = new MailArchive();
        org.springframework.test.util.ReflectionTestUtils.setField(archive, "dataRootResolver",
                new com.lilac.config.DataRootResolver(root.toString()));
        return archive;
    }
}
