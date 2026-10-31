package com.lilac.init;

import com.lilac.config.DataRootResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 启动时创建 data 目录，避免首次写入时才暴露路径问题
 */
@Slf4j
@Component
public class DataDirectoryInitializer implements ApplicationRunner {

    private static final List<String> SUB_DIRECTORIES = List.of("report", "op-log", "mail", "screenshot");

    @Resource
    private DataRootResolver dataRootResolver;

    /**
     * 创建 data 根目录与四个子目录，已存在则跳过
     *
     * @param args 启动参数
     * @throws IOException 目录创建失败
     */
    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path root = dataRootResolver.root();
        Files.createDirectories(root);
        for (String sub : SUB_DIRECTORIES) {
            Files.createDirectories(root.resolve(sub));
        }
        log.info("data 目录就绪: {}", root);
    }
}
