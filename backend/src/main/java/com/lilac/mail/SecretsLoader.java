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
     * @return 键值表
     */
    public Map<String, String> load(String file) {
        Map<String, String> secrets = new LinkedHashMap<>();
        if (file == null || file.isBlank()) {
            return secrets;
        }
        Path path = Paths.get(file).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.info("密钥文件不存在，按未配置处理：{}", path);
            return secrets;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                putLine(secrets, line);
            }
        } catch (IOException e) {
            // 只吐路径与异常类型，异常消息里可能带内容片段
            log.warn("密钥文件读取失败 {}：{}", path, e.getClass().getSimpleName());
        }
        return secrets;
    }

    /**
     * 解析一行 KEY=VALUE，注释与空行跳过
     *
     * @param secrets 键值表
     * @param line    原始行
     */
    private void putLine(Map<String, String> secrets, String line) {
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
        // 值两侧允许带引号，去掉后才是真正的授权码
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        secrets.put(key, value);
    }
}
