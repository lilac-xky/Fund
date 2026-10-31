package com.lilac.mail;

import com.lilac.config.DataRootResolver;
import com.lilac.domain.vo.MailArchiveVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 邮件内容留存，发送失败时把正文落到 data/mail，下次成功发送时读出来附在补发段
 */
@Slf4j
@Component
public class MailArchive {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter META_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 失败件在文件名里的标记，补发只看这一种
     */
    private static final String FAILED_TAG = "-failed-";

    /**
     * 已补发标记文件的后缀，避免同一封失败件被反复补发
     */
    private static final String RESENT_MARK = ".resent";

    @Resource
    private DataRootResolver dataRootResolver;

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
     * 取待补发的失败件，含当天失败（改好口令后当天就补发是验收要求），已补发过的不再重复
     *
     * @param tradeDate 本次发送的交易日，晚于该日期的留存不计入
     * @param limit     最多取几封，过多会把邮件撑爆
     * @return 待补发项，每项带日期、主题与文件
     */
    public List<Pending> pendingResend(LocalDate tradeDate, int limit) {
        List<Pending> items = new ArrayList<>();
        try {
            Path dir = mailDir();
            if (!Files.isDirectory(dir)) {
                return items;
            }
            try (var stream = Files.list(dir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(this::isPendingFailure)
                        .filter(path -> !isAfter(path, tradeDate))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                Comparator.reverseOrder()))
                        .limit(Math.max(1, limit))
                        .toList();
                for (Path file : files) {
                    items.add(new Pending(file, summaryOf(file)));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("读取未送达内容失败：{}", e.getMessage());
        }
        return items;
    }

    /**
     * 把失败件标记为已补发，下次不再重复列出
     *
     * @param pending 已补发的失败件
     * @return 是否全部标记成功
     */
    public boolean markResent(List<Pending> pending) {
        boolean all = true;
        for (Pending item : pending) {
            try {
                Path mark = item.getFile().resolveSibling(item.getFile().getFileName() + RESENT_MARK);
                Files.writeString(mark, LocalDateTime.now().format(META_STAMP), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                all = false;
                log.warn("补发标记写入失败 {}：{}", item.getFile().getFileName(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 判断一份留存是否为待补发的失败件，成功件与已标记的不算
     *
     * @param file 留存文件
     * @return 待补发返回 true
     */
    private boolean isPendingFailure(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".txt") || !name.contains(FAILED_TAG)) {
            return false;
        }
        return !Files.exists(file.resolveSibling(name + RESENT_MARK));
    }

    /**
     * 取邮件留存目录
     *
     * @return 目录路径
     */
    private Path mailDir() {
        return dataRootResolver.root().resolve("mail");
    }

    /**
     * 取邮件留存目录的绝对路径，供页面直接告诉使用者「文件在哪」
     *
     * @return 绝对路径文本
     */
    public String mailDirText() {
        return mailDir().toString();
    }

    /**
     * 扫描留存目录，按时间倒序列出最近的已发送与未送达件，供调度页展示
     *
     * @param limit 最多取几条
     * @return 留存清单，目录不存在时为空
     */
    public List<MailArchiveVO> listRecent(int limit) {
        List<MailArchiveVO> items = new ArrayList<>();
        try {
            Path dir = mailDir();
            if (!Files.isDirectory(dir)) {
                return items;
            }
            try (var stream = Files.list(dir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".txt"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                Comparator.reverseOrder()))
                        .limit(Math.max(1, limit))
                        .toList();
                for (Path file : files) {
                    items.add(toVO(file));
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("扫描邮件留存目录失败：{}", e.getMessage());
        }
        return items;
    }

    /**
     * 统计尚未补发的失败件数量，供调度页提示「还有几封没送出去」
     *
     * @return 待补发数量
     */
    public int pendingResendCount() {
        try {
            Path dir = mailDir();
            if (!Files.isDirectory(dir)) {
                return 0;
            }
            try (var stream = Files.list(dir)) {
                return (int) stream.filter(Files::isRegularFile).filter(this::isPendingFailure).count();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("统计未送达留存失败：{}", e.getMessage());
            return 0;
        }
    }

    /**
     * 把一份留存文件转成视图，失败件标记出来供页面标红
     *
     * @param file 留存文件
     * @return 留存视图
     */
    private MailArchiveVO toVO(Path file) {
        MailArchiveVO vo = new MailArchiveVO();
        String name = file.getFileName().toString();
        vo.setFileName(name);
        boolean failed = name.contains(FAILED_TAG);
        vo.setFailed(failed);
        vo.setResent(Files.exists(file.resolveSibling(name + RESENT_MARK)));
        vo.setSizeBytes(file.toFile().length());
        try {
            // 留存时间取文件修改时间，比文件名里的时分秒更可靠
            vo.setArchivedAt(java.time.LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(file).toInstant(), java.time.ZoneId.systemDefault()));
        } catch (IOException ignored) {
            // 属性读不到不影响展示，时间字段留空
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("发送时间：")) {
                    vo.setSentAt(line.substring("发送时间：".length()).trim());
                } else if (line.startsWith("交易日：")) {
                    vo.setTradeDate(line.substring("交易日：".length()).trim());
                } else if (line.startsWith("主题：")) {
                    vo.setSubject(line.substring("主题：".length()).trim());
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            vo.setSubject("（留存文件读取失败）");
        }
        if (vo.getSubject() == null) {
            vo.setSubject("（无主题记录）");
        }
        return vo;
    }

    /**
     * 判断留存文件是否晚于指定日期，晚于当天的不该算进本次补发
     *
     * @param file 留存文件，文件名以交易日开头
     * @param date 基准日期
     * @return 晚于返回 true
     */
    private boolean isAfter(Path file, LocalDate date) {
        String name = file.getFileName().toString();
        try {
            return LocalDate.parse(name.substring(0, 10)).isAfter(date);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * 摘出留存文件的日期与主题，供补发段直接展示
     *
     * @param file 留存文件
     * @return 摘要
     */
    private String summaryOf(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
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
        Path root = dataRootResolver.root();
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * 一份待补发的失败件
     */
    public static class Pending {

        private final Path file;
        private final String summary;

        /**
         * 记录文件与摘要
         *
         * @param file    留存文件
         * @param summary 摘要
         */
        Pending(Path file, String summary) {
            this.file = file;
            this.summary = summary;
        }

        /**
         * 取留存文件
         *
         * @return 文件
         */
        public Path getFile() {
            return file;
        }

        /**
         * 取摘要文字
         *
         * @return 摘要
         */
        public String getSummary() {
            return summary;
        }
    }
}
