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
import java.util.Map;

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
                "OTHER=single"), StandardCharsets.UTF_8);

        Map<String, String> secrets = secretsLoader.load(file.toString());
        assertThat(secrets).containsEntry("SMTP_PASSWORD", "abc123").containsEntry("OTHER", "single");
        assertThat(secrets).doesNotContainKey("# 注释行应被跳过");
    }

    /**
     * 文件不存在时返回空表而不是抛异常，页面据空表显示「未配置」
     */
    @Test
    @DisplayName("密钥文件缺失时返回空表")
    void missingFileReturnsEmpty() {
        assertThat(secretsLoader.load(tempDir.resolve("nope.env").toString())).isEmpty();
        assertThat(secretsLoader.load(null)).isEmpty();
    }

    /**
     * 留存能把主题与正文写到 data/mail，并按「早于某日」筛出来
     */
    @Test
    @DisplayName("留存可写入并按日期读出未送达内容")
    void archiveWritesAndReads() {
        MailArchive archive = archiveAt(tempDir);
        String path = archive.save(LocalDate.of(2026, 10, 29), "failed", "收盘前提醒 2026-10-29",
                "<p>html</p>", "text 正文");

        assertThat(path).startsWith("mail/").endsWith(".txt");
        List<String> pending = archive.pendingResend(LocalDate.of(2026, 10, 30), 5);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0)).contains("2026-10-29").contains("收盘前提醒");
    }

    /**
     * 当天留存的邮件不算「此前未送达」，避免刚失败就被当成历史补发
     */
    @Test
    @DisplayName("当天留存不算此前未送达")
    void todayArchiveNotPending() {
        MailArchive archive = archiveAt(tempDir);
        archive.save(LocalDate.of(2026, 10, 30), "failed", "今天失败", "<p></p>", "text");
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
        org.springframework.test.util.ReflectionTestUtils.setField(archive, "dataRoot", root.toString());
        return archive;
    }
}
