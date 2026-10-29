package com.lilac.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lilac.controller.AuthController;
import com.lilac.domain.dto.auth.LoginRequest;
import com.lilac.domain.vo.AuthStatusVO;
import com.lilac.mail.SecretsLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密钥隔离核查，断言口令既不出现在任何接口返回里，也不出现在应用日志里
 */
class SecretsIsolationTest {

    private static final String USERNAME = "lilac";
    private static final String PASSWORD = "uNr34lZ9-must-never-leak-7Kq";

    @TempDir
    Path tempDir;

    private AuthProperties properties;
    private AuthController controller;
    private ListAppender<ILoggingEvent> appended;
    private Logger logger;

    /**
     * 装配登录链路并挂上日志捕获器
     *
     * @throws IOException 写密钥文件失败时抛出
     */
    @BeforeEach
    void setUp() throws IOException {
        properties = new AuthProperties();
        properties.setEnabled(true);
        properties.setSecretsFile(tempDir.resolve("secrets.env").toString());
        properties.setMaxFailures(5);
        properties.setLockMinutes(15);
        properties.setSessionHours(12);
        properties.setCookieName("fund_session");
        SecretsLoader loader = new SecretsLoader();
        CredentialResolver credentialResolver = new CredentialResolver(loader, properties);
        SessionRegistry registry = new SessionRegistry(properties);
        LoginAttemptGuard guard = new LoginAttemptGuard(properties);
        controller = new AuthController(properties, credentialResolver, registry, guard);
        Files.writeString(tempDir.resolve("secrets.env"),
                "AUTH_USERNAME=" + USERNAME + "\nAUTH_PASSWORD=" + PASSWORD + "\n",
                StandardCharsets.UTF_8);

        logger = (Logger) LoggerFactory.getLogger("com.lilac");
        appended = new ListAppender<>();
        appended.start();
        logger.addAppender(appended);
    }

    /**
     * 用例结束后摘掉日志捕获器，避免影响其他测试
     */
    @AfterEach
    void tearDown() {
        logger.detachAppender(appended);
        appended.stop();
    }

    /**
     * 登录成功后接口返回里不含口令，也不含用户名以外的凭据
     */
    @Test
    @DisplayName("登录接口返回不含口令")
    void loginResponseNeverContainsPassword() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthStatusVO status = controller.login(loginOf(PASSWORD), response).getData();

        assertThat(status.toString()).doesNotContain(PASSWORD);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(PASSWORD);
    }

    /**
     * 登录失败时返回的提示里不含口令
     */
    @Test
    @DisplayName("登录失败提示不含口令")
    void failureMessageNeverContainsPassword() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            controller.login(loginOf("wrong-" + PASSWORD), response);
        } catch (RuntimeException ignored) {
            // 这里只关心返回内容与日志，异常本身由别的用例断言
        }

        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(PASSWORD);
    }

    /**
     * 状态接口只回是否配置，不回口令
     */
    @Test
    @DisplayName("状态接口不含口令")
    void statusNeverContainsPassword() {
        AuthStatusVO status = controller.status(new MockHttpServletRequest("GET", "/api/auth/status")).getData();

        assertThat(status.getConfigured()).isTrue();
        assertThat(status.toString()).doesNotContain(PASSWORD);
    }

    /**
     * 成功与失败的整条链路跑下来，日志里都不出现口令
     */
    @Test
    @DisplayName("日志中不出现口令")
    void logsNeverContainPassword() {
        controller.login(loginOf(PASSWORD), new MockHttpServletResponse());
        try {
            controller.login(loginOf("bad"), new MockHttpServletResponse());
        } catch (RuntimeException ignored) {
            // 失败路径同样要检查日志
        }

        List<String> logged = appended.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logged).isNotEmpty();
        assertThat(logged).noneMatch(message -> message.contains(PASSWORD));
    }

    /**
     * 构造登录请求
     *
     * @param password 口令
     * @return 登录请求
     */
    private LoginRequest loginOf(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(USERNAME);
        request.setPassword(password);
        return request;
    }
}
