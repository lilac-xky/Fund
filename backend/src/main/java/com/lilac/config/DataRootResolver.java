package com.lilac.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * data 目录统一解析器：相对配置一律锚定到仓库根（pom.xml 所在的上一级），与进程启动目录无关
 */
@Component
public class DataRootResolver {

    /**
     * 锚点文件的文件名，向上搜索到它即认为到达仓库根
     */
    private static final String ANCHOR_FILE = "pom.xml";

    private final String configuredRoot;

    /**
     * 注入相对配置，供运行时解析成绝对路径
     *
     * @param configuredRoot 配置里的 data 根目录，默认 data
     */
    public DataRootResolver(@Value("${fund.data.root:data}") String configuredRoot) {
        this.configuredRoot = configuredRoot;
    }

    /**
     * 取 data 根目录的绝对路径；相对路径锚定仓库根，绝对路径原样使用
     *
     * @return 已规范化目录路径，尚未创建时也存在
     */
    public Path root() {
        Path configured = Paths.get(configuredRoot).toAbsolutePath().normalize();
        if (configuredRoot.startsWith("/") || configuredRoot.matches("^[A-Za-z]:[\\\\/].*")) {
            return configured;
        }
        return repoRoot().resolve(configuredRoot).normalize();
    }

    /**
     * 向上搜索仓库根（含 pom.xml 的目录的上一级），找不到时退回进程工作目录
     *
     * @return 仓库根目录
     */
    private Path repoRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path cursor = cwd;
        while (cursor != null) {
            if (Files.exists(cursor.resolve(ANCHOR_FILE)) && Files.exists(cursor.resolve("backend"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return cwd;
    }
}
