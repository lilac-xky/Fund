package com.lilac.auth;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录会话管理，令牌存在内存里，重启即失效，登出与超时都能立刻作废
 */
@Slf4j
@Component
public class SessionRegistry {

    private static final int TOKEN_BYTES = 32;

    private final AuthProperties authProperties;

    private final SecureRandom random = new SecureRandom();

    /** 令牌到会话的映射 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 注入登录配置
     *
     * @param authProperties 登录配置
     */
    public SessionRegistry(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 新建一个会话并返回令牌
     *
     * @param username 用户名
     * @return 会话令牌
     */
    public String create(String username) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session();
        session.username = username;
        session.expiresAt = Instant.now().plus(Duration.ofHours(Math.max(1, authProperties.getSessionHours())));
        sessions.put(token, session);
        return token;
    }

    /**
     * 校验令牌，超时的会话当场移除
     *
     * @param token 会话令牌
     * @return 会话有效返回 true
     */
    public boolean valid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (Instant.now().isAfter(session.expiresAt)) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 作废一个会话，登出时调用
     *
     * @param token 会话令牌
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * 清理已超时的会话，避免长跑后内存里堆着过期记录
     *
     * @return 本次清理的条数
     */
    public int purgeExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));
        return before - sessions.size();
    }

    /**
     * 当前有效会话数，供测试断言
     *
     * @return 会话数
     */
    public int size() {
        return sessions.size();
    }

    /**
     * 一个登录会话
     */
    @Data
    private static class Session {

        /** 登录用户名 */
        private String username;

        /** 过期时间 */
        private Instant expiresAt;
    }
}
