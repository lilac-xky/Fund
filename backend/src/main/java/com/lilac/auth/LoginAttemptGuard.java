package com.lilac.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数，连续失败达到阈值后临时锁定，超过时长自动解锁
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    private final AuthProperties authProperties;

    /** 键为用户名，值为失败次数与锁定截止时间 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 注入登录配置
     *
     * @param authProperties 登录配置
     */
    public LoginAttemptGuard(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 判断该用户名当前是否被锁定
     *
     * @param username 用户名
     * @return 锁定的剩余秒数，未锁定返回 0
     */
    public long lockedSeconds(String username) {
        Attempt attempt = attempts.get(key(username));
        if (attempt == null || attempt.lockedUntil == null) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), attempt.lockedUntil).getSeconds();
        if (remaining <= 0) {
            // 锁定到期即清空计数，让使用者能重新尝试而不是继续被旧记录挡住
            attempts.remove(key(username));
            return 0;
        }
        return remaining;
    }

    /**
     * 记录一次失败，达到阈值时开始锁定
     *
     * @param username 用户名
     * @return 记录后的失败次数
     */
    public int recordFailure(String username) {
        String key = key(username);
        Attempt attempt = attempts.computeIfAbsent(key, ignored -> new Attempt());
        attempt.failures++;
        if (attempt.failures >= Math.max(1, authProperties.getMaxFailures())) {
            attempt.lockedUntil = Instant.now().plus(Duration.ofMinutes(Math.max(1, authProperties.getLockMinutes())));
            log.warn("登录失败次数达到阈值，已临时锁定 {} 分钟", authProperties.getLockMinutes());
        }
        return attempt.failures;
    }

    /**
     * 登录成功后清空该用户的失败计数
     *
     * @param username 用户名
     */
    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /**
     * 取键名，用户名一律按小写归并，避免大小写绕过计数
     *
     * @param username 用户名
     * @return 键名
     */
    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    /**
     * 一个用户名的失败计数与锁定截止时间
     */
    private static class Attempt {

        /** 连续失败次数 */
        private int failures;

        /** 锁定截止时间，未锁定为 null */
        private Instant lockedUntil;
    }
}
