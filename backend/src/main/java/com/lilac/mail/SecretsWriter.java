package com.lilac.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 密钥文件写入器，只负责把指定键写回 config/secrets.env，从不回读内容也不打日志
 */
@Slf4j
@Component
public class SecretsWriter {

    /**
     * 写入一个或多个键，保留文件里其他键与注释，只替换同名键的值
     *
     * @param file   密钥文件路径
     * @param values 待写入的键值，值为 null 表示删除该键
     * @return 写入结果说明，失败时给出原因
     */
    public WriteResult write(String file, Map<String, String> values) {
        WriteResult result = new WriteResult();
        if (file == null || file.isBlank()) {
            result.setOk(false);
            result.setMessage("密钥文件路径未配置");
            return result;
        }
        if (values == null || values.isEmpty()) {
            result.setOk(true);
            result.setMessage("没有需要写入的项");
            return result;
        }
        Path path = Paths.get(file).toAbsolutePath().normalize();
        try {
            Map<String, String> remaining = new LinkedHashMap<>(values);
            List<String> lines = Files.isRegularFile(path)
                    ? Files.readAllLines(path, StandardCharsets.UTF_8)
                    : List.of();
            StringBuilder builder = new StringBuilder();
            for (String line : lines) {
                String key = keyOf(line);
                if (key != null && remaining.containsKey(key)) {
                    String value = remaining.remove(key);
                    if (value != null && !value.isBlank()) {
                        builder.append(key).append('=').append(value.trim()).append(System.lineSeparator());
                    }
                    // 值为空表示删除，该键直接丢掉即可
                    continue;
                }
                builder.append(line).append(System.lineSeparator());
            }
            for (Map.Entry<String, String> entry : remaining.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    builder.append(entry.getKey()).append('=').append(entry.getValue().trim())
                            .append(System.lineSeparator());
                }
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
            result.setOk(true);
            result.setMessage("已写入密钥文件");
            return result;
        } catch (IOException | RuntimeException e) {
            // 只吐异常类型，异常消息里可能带文件片段
            log.warn("密钥文件写入失败 {}：{}", path, e.getClass().getSimpleName());
            result.setOk(false);
            result.setMessage("密钥文件写入失败：" + e.getClass().getSimpleName());
            return result;
        }
    }

    /**
     * 取一行里的键名，注释与空行返回 null
     *
     * @param line 原始行
     * @return 键名
     */
    private String keyOf(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int index = trimmed.indexOf('=');
        if (index <= 0) {
            return null;
        }
        return trimmed.substring(0, index).trim();
    }

    /**
     * 密钥写入结果
     */
    public static class WriteResult {

        private boolean ok;

        private String message;

        /**
         * 是否写入成功
         *
         * @return 成功返回 true
         */
        public boolean isOk() {
            return ok;
        }

        /**
         * 设置是否成功
         *
         * @param ok 成功标记
         */
        public void setOk(boolean ok) {
            this.ok = ok;
        }

        /**
         * 取结果说明
         *
         * @return 说明
         */
        public String getMessage() {
            return message;
        }

        /**
         * 设置结果说明
         *
         * @param message 说明
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
