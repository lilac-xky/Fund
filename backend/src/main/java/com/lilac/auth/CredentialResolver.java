package com.lilac.auth;

import com.lilac.mail.SecretsLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 登录凭据读取与校验，口令只从密钥文件读、只在内存里比对，任何接口与日志都不回显
 */
@Slf4j
@Component
public class CredentialResolver {

    /**
     * 用户名键名
     */
    public static final String KEY_USERNAME = "AUTH_USERNAME";

    /**
     * 口令键名
     */
    public static final String KEY_PASSWORD = "AUTH_PASSWORD";

    private final SecretsLoader secretsLoader;

    private final AuthProperties authProperties;

    /**
     * 注入密钥读取与登录配置
     *
     * @param secretsLoader  密钥读取
     * @param authProperties 登录配置
     */
    public CredentialResolver(SecretsLoader secretsLoader, AuthProperties authProperties) {
        this.secretsLoader = secretsLoader;
        this.authProperties = authProperties;
    }

    /**
     * 判断是否已配置可用的用户名与口令
     *
     * @return 都已配置返回 true
     */
    public boolean configured() {
        SecretsLoader.Secrets secrets = load();
        return notBlank(secrets.get(KEY_USERNAME)) && notBlank(secrets.get(KEY_PASSWORD));
    }

    /**
     * 校验用户名与口令，用户名与口令都比对，失败一律返回 false 不区分哪一项错
     *
     * @param username 输入的用户名
     * @param password 输入的口令
     * @return 通过返回 true
     */
    public boolean matches(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        SecretsLoader.Secrets secrets = load();
        String expectedUser = secrets.get(KEY_USERNAME);
        String expectedPassword = secrets.get(KEY_PASSWORD);
        if (!notBlank(expectedUser) || !notBlank(expectedPassword)) {
            return false;
        }
        // 两项都做定长比较，避免用户名错时提前返回带来的时序差异
        boolean userOk = constantTimeEquals(expectedUser, username);
        boolean passwordOk = constantTimeEquals(expectedPassword, password);
        return userOk && passwordOk;
    }

    /**
     * 读密钥文件，读不到时返回空表，由调用方给出「未配置」结论
     *
     * @return 密钥
     */
    private SecretsLoader.Secrets load() {
        return secretsLoader.loadAll(authProperties.getSecretsFile());
    }

    /**
     * 定长比较，长度不同也走完整个循环，避免按长度泄露信息
     *
     * @param expected 期望值
     * @param actual   实际值
     * @return 相等返回 true
     */
    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        int diff = left.length ^ right.length;
        if (right.length == 0) {
            return false;
        }
        for (int index = 0; index < left.length; index++) {
            diff |= left[index] ^ right[index % right.length];
        }
        return diff == 0;
    }

    /**
     * 判断字符串非空
     *
     * @param value 原文
     * @return 非空返回 true
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
