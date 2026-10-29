package com.lilac.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话管理测试，核心是「令牌随机且唯一」「超时作废」「登出立即失效」
 */
class SessionRegistryTest {

    private AuthProperties properties;
    private SessionRegistry registry;

    /**
     * 会话有效期设为 12 小时
     */
    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setSessionHours(12);
        registry = new SessionRegistry(properties);
    }

    /**
     * 新建的会话立即有效，且令牌长度足够
     */
    @Test
    @DisplayName("新建会话立即有效")
    void createdSessionIsValid() {
        String token = registry.create("lilac");

        assertThat(token).isNotBlank();
        // 32 字节 base64url 无填充后为 43 个字符，太短说明随机源不够
        assertThat(token.length()).isGreaterThanOrEqualTo(40);
        assertThat(registry.valid(token)).isTrue();
    }

    /**
     * 两次登录拿到不同令牌，不能复用
     */
    @Test
    @DisplayName("每次登录令牌都不同")
    void tokensAreUnique() {
        String first = registry.create("lilac");
        String second = registry.create("lilac");

        assertThat(first).isNotEqualTo(second);
        assertThat(registry.valid(first)).isTrue();
        assertThat(registry.valid(second)).isTrue();
    }

    /**
     * 登出后令牌立即失效
     */
    @Test
    @DisplayName("登出后令牌立即失效")
    void invalidatedTokenRejected() {
        String token = registry.create("lilac");
        registry.invalidate(token);

        assertThat(registry.valid(token)).isFalse();
    }

    /**
     * 空令牌与伪造令牌都判为无效
     */
    @Test
    @DisplayName("空令牌与伪造令牌无效")
    void bogusTokensRejected() {
        assertThat(registry.valid(null)).isFalse();
        assertThat(registry.valid("")).isFalse();
        assertThat(registry.valid("forged-token")).isFalse();
    }

    /**
     * 清理过期会话时只删超时的，未超时的保留
     */
    @Test
    @DisplayName("清理过期会话保留有效会话")
    void purgeKeepsValidSessions() {
        String token = registry.create("lilac");

        assertThat(registry.purgeExpired()).isZero();
        assertThat(registry.valid(token)).isTrue();
    }
}
