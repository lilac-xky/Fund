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
 * 密钥文件读取，只解析 config/secrets.env 的键值，不写日志、不回显
 */
@Slf4j
@Component
public class SecretsLoader {

    /**
     * 读密钥文件为键值表，文件不存在或读不到时返回空表，由调用方给出「未配置」结论
     *
     * @param file 密钥文件路径
     * @return 键值
     */
    public Secrets loadAll(String file) {
        Map<String, String> values = new LinkedHashMap<>();
        if (file == null || file.isBlank()) {
            return new Secrets(values);
        }
        Path path = Paths.get(file).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.info("密钥文件不存在，按未配置处理：{}", path);
            return new Secrets(values);
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                putLine(values, line);
            }
        } catch (IOException e) {
            // 只吐路径与异常类型，异常消息里可能带内容片段
            log.warn("密钥文件读取失败 {}：{}", path, e.getClass().getSimpleName());
        }
        return new Secrets(values);
    }

    /**
     * 解析一行 KEY=VALUE，注释与空行跳过，值两侧引号去掉
     *
     * @param values 键值表
     * @param line   原始行
     */
    private void putLine(Map<String, String> values, String line) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        int index = trimmed.indexOf('=');
        if (index <= 0) {
            return;
        }
        String key = trimmed.substring(0, index).trim();
        String value = trimmed.substring(index + 1).trim();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        values.put(key, value);
    }

    /**
     * 密钥键值只读视图，避免调用方拿到原始 Map 后随手打印
     */
    public static class Secrets {

        private final Map<String, String> values;

        /**
         * 包装键值表
         *
         * @param values 键值表
         */
        Secrets(Map<String, String> values) {
            this.values = values;
        }

        /**
         * 按键取值
         *
         * @param key 键名
         * @return 值，不存在返回 null
         */
        public String get(String key) {
            return values.get(key);
        }

        /**
         * 判断是否没有任何键，供测试与诊断使用
         *
         * @return 空返回 true
         */
        public boolean isEmpty() {
            return values.isEmpty();
        }
    }
}
