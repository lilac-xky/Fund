package com.lilac.auth;

import com.lilac.controller.AuthController;
import com.lilac.domain.dto.auth.LoginRequest;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mail.SecretsLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录链路测试，覆盖「未登录被拒」「登录后放行」「锁定后拒绝」「登出失效」
 */
class AuthFlowTest {

    private static final String USERNAME = "lilac";
    private static final String PASSWORD = "s3cr3t-P@ssw0rd";

    @TempDir
    Path tempDir;

    private AuthProperties properties;
    private CredentialResolver credentialResolver;
    private SessionRegistry sessionRegistry;
    private LoginAttemptGuard guard;
    private AuthController controller;
    private AuthInterceptor interceptor;

    /**
     * 用临时密钥文件装配整条登录链路
     *
     * @throws IOException 写密钥文件失败时抛出
     */
    @BeforeEach
    void setUp() throws IOException {
        properties = new AuthProperties();
        properties.setEnabled(true);
        properties.setSecretsFile(tempDir.resolve("secrets.env").toString());
        properties.setMaxFailures(3);
        properties.setLockMinutes(15);
        properties.setSessionHours(12);
        properties.setCookieName("fund_session");
        SecretsLoader loader = new SecretsLoader();
        credentialResolver = new CredentialResolver(loader, properties);
        sessionRegistry = new SessionRegistry(properties);
        guard = new LoginAttemptGuard(properties);
        controller = new AuthController(properties, credentialResolver, sessionRegistry, guard);
        interceptor = new AuthInterceptor(properties, sessionRegistry);
        Files.writeString(tempDir.resolve("secrets.env"),
                "AUTH_USERNAME=" + USERNAME + "\nAUTH_PASSWORD=" + PASSWORD + "\n",
                StandardCharsets.UTF_8);
    }

    /**
     * 未带 Cookie 的请求被拦截，返回 401 与 NEED_LOGIN
     */
    @Test
    @DisplayName("未登录的接口请求被拒绝")
    void unauthenticatedRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/holding/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains(String.valueOf(HttpsCodeEnum.NEED_LOGIN.getCode()));
    }

    /**
     * 登录成功后写入 HttpOnly Cookie，再用该 Cookie 请求即放行
     */
    @Test
    @DisplayName("登录后携带 Cookie 即放行")
    void loginThenAccessAllowed() throws Exception {
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        controller.login(loginOf(USERNAME, PASSWORD), loginResponse);

        String cookie = loginResponse.getCookie("fund_session").getValue();
        assertThat(cookie).isNotBlank();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/holding/overview");
        request.setCookies(new jakarta.servlet.http.Cookie("fund_session", cookie));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    /**
     * 会话 Cookie 必须是 HttpOnly，脚本读不到才不会轻易被 XSS 拿走
     */
    @Test
    @DisplayName("会话 Cookie 为 HttpOnly")
    void sessionCookieIsHttpOnly() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.login(loginOf(USERNAME, PASSWORD), response);

        assertThat(response.getCookie("fund_session").isHttpOnly()).isTrue();
    }

    /**
     * 口令错误时抛出用户名或口令错误，且响应里不含任何口令片段
     */
    @Test
    @DisplayName("口令错误时拒绝且不回显口令")
    void wrongPasswordRejected() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.login(loginOf(USERNAME, "wrong-password"), response))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或口令不正确")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(PASSWORD, "wrong-password"));
    }

    /**
     * 连续失败达到阈值后锁定，即使口令正确也拒绝
     */
    @Test
    @DisplayName("连续失败达到阈值后锁定")
    void lockedAfterRepeatedFailures() {
        for (int index = 0; index < 3; index++) {
            assertThatThrownBy(() -> controller.login(loginOf(USERNAME, "bad"), new MockHttpServletResponse()))
                    .isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> controller.login(loginOf(USERNAME, PASSWORD), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已临时锁定");
    }

    /**
     * 锁定期间不校验口令，避免正确的口令把锁定状态刷掉
     */
    @Test
    @DisplayName("锁定期间正确口令也被拒绝")
    void lockedRejectsEvenCorrectPassword() {
        guard.recordFailure(USERNAME);
        guard.recordFailure(USERNAME);
        guard.recordFailure(USERNAME);

        assertThatThrownBy(() -> controller.login(loginOf(USERNAME, PASSWORD), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("锁定");
    }

    /**
     * 登出后原来的令牌立即失效
     */
    @Test
    @DisplayName("登出后令牌失效")
    void logoutInvalidatesSession() throws Exception {
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        controller.login(loginOf(USERNAME, PASSWORD), loginResponse);
        String cookie = loginResponse.getCookie("fund_session").getValue();

        MockHttpServletRequest logoutRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        logoutRequest.setCookies(new jakarta.servlet.http.Cookie("fund_session", cookie));
        controller.logout(logoutRequest, new MockHttpServletResponse());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/holding/overview");
        request.setCookies(new jakarta.servlet.http.Cookie("fund_session", cookie));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
    }

    /**
     * 登录开关关闭时不拦截任何请求，仅供本机开发
     */
    @Test
    @DisplayName("登录开关关闭时不拦截")
    void disabledSkipsAuth() throws Exception {
        properties.setEnabled(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/holding/overview");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    /**
     * 状态接口在未登录时也返回 200，由前端据 authenticated 决定是否跳转
     */
    @Test
    @DisplayName("未登录时状态接口仍返回 200")
    void statusAlwaysOk() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/status");

        var result = controller.status(request);

        assertThat(result.getCode()).isEqualTo(HttpsCodeEnum.SUCCESS.getCode());
        assertThat(result.getData().getAuthenticated()).isFalse();
        assertThat(result.getData().getRequired()).isTrue();
        assertThat(result.getData().getConfigured()).isTrue();
    }

    /**
     * 凭据未配置时任何人都登不进来，并给出可操作的原因
     */
    @Test
    @DisplayName("凭据未配置时拒绝登录并说明原因")
    void unconfiguredRejectsLogin() throws IOException {
        Files.writeString(tempDir.resolve("secrets.env"), "AUTH_USERNAME=\nAUTH_PASSWORD=\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> controller.login(loginOf(USERNAME, PASSWORD), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AUTH_USERNAME");
    }

    /**
     * 构造登录请求
     *
     * @param username 用户名
     * @param password 口令
     * @return 登录请求
     */
    private LoginRequest loginOf(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
