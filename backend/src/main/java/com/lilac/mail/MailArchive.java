package com.lilac.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 邮件内容留存，发送失败时把正文落到 data/mail，下次成功发送时读出来附在补发段
 */
@Slf4j
@Component
public class MailArchive {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter META_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${fund.data.root:data}")
    private String dataRoot;

    /**
     * 留存一封邮件，返回相对 data 目录的路径；留存失败只记日志，不改变发送结论
     *
     * @param tradeDate 交易日
     * @param tag       文件名里用于区分场景的后缀
     * @param subject   邮件主题
     * @param html      HTML 正文
     * @param text      纯文本正文
     * @return 相对路径，失败返回 null
     */
    public String save(LocalDate tradeDate, String tag, String subject, String html, String text) {
        try {
            Path dir = mailDir();
            Files.createDirectories(dir);
            String fileName = tradeDate + "-" + tag + "-" + LocalDateTime.now().format(FILE_STAMP) + ".txt";
            StringBuilder builder = new StringBuilder();
            builder.append("发送时间：").append(LocalDateTime.now().format(META_STAMP)).append(System.lineSeparator());
            builder.append("交易日：").append(tradeDate).append(System.lineSeparator());
            builder.append("主题：").append(subject).append(System.lineSeparator());
            builder.append("---- 纯文本正文 ----").append(System.lineSeparator());
            builder.append(text == null ? "" : text).append(System.lineSeparator());
            builder.append("---- HTML 正文 ----").append(System.lineSeparator());
            builder.append(html == null ? "" : html).append(System.lineSeparator());
            Files.writeString(dir.resolve(fileName), builder.toString(), StandardCharsets.UTF_8);
            return relativeOf(dir.resolve(fileName));
        } catch (IOException | RuntimeException e) {
            log.warn("邮件内容留存失败 {} {}：{}", tradeDate, tag, e.getMessage());
            return null;
        }
    }

    /**
     * 列出此前未送达的纯文本正文，供下次成功发送时附带补发
     *
     * @param beforeDate 只取该日期之前的留存，避免把今天的算成历史
     * @param limit      最多取几封，过多会把邮件撑爆
     * @return 未送达内容，每项为「日期 + 正文摘要」
     */
    public java.util.List<String> pendingResend(LocalDate beforeDate, int limit) {
        java.util.List<String> items = new java.util.ArrayList<>();
        try {
            Path dir = mailDir();
            if (!Files.isDirectory(dir)) {
                return items;
            }
            try (var stream = Files.list(dir)) {
                java.util.List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".txt"))
                        .filter(path -> isBefore(path, beforeDate))
                        .sorted(java.util.Comparator.comparing(path ->
                                path.getFileName().toString(), java.util.Comparator.reverseOrder()))
                        .limit(Math.max(1, limit))
                        .toList();
                for (Path file : files) {
                    items.add(summaryOf(file));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("读取未送达内容失败：{}", e.getMessage());
        }
        return items;
    }

    /**
     * 取邮件留存目录
     *
     * @return 目录路径
     */
    private Path mailDir() {
        return Paths.get(dataRoot).toAbsolutePath().normalize().resolve("mail");
    }

    /**
     * 判断留存文件是否早于指定日期，文件名以交易日开头
     *
     * @param file 留存文件
     * @param date 基准日期
     * @return 早于返回 true
     */
    private boolean isBefore(Path file, LocalDate date) {
        String name = file.getFileName().toString();
        try {
            return LocalDate.parse(name.substring(0, 10)).isBefore(date);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 摘出留存文件的首行与主题，供补发段直接展示
     *
     * @param file 留存文件
     * @return 摘要
     */
    private String summaryOf(Path file) {
        try {
            java.util.List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String subject = lines.stream().filter(line -> line.startsWith("主题："))
                    .findFirst().map(line -> line.substring(3)).orElse("（无主题记录）");
            return file.getFileName().toString().substring(0, 10) + " " + subject;
        } catch (IOException e) {
            return file.getFileName().toString();
        }
    }

    /**
     * 把绝对路径转成相对 data 目录的路径，留痕里存相对路径便于整目录搬移
     *
     * @param file 文件路径
     * @return 相对路径
     */
    private String relativeOf(Path file) {
        Path root = Paths.get(dataRoot).toAbsolutePath().normalize();
        return root.relativize(file).toString().replace('\\', '/');
    }
}
