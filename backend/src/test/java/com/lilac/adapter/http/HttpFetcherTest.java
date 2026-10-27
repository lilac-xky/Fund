package com.lilac.adapter.http;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP 封装测试，用本地服务端核对 UA、BOM 与失败处理
 */
class HttpFetcherTest {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TestRunner";

    private HttpServer server;
    private final List<String> receivedUserAgents = new ArrayList<>();

    /**
     * 启动本地服务端，记录收到的 User-Agent 并返回带 BOM 的文本
     *
     * @throws Exception 端口绑定失败
     */
    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            receivedUserAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "\uFEFFvar r = [];".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
    }

    /**
     * 停止本地服务端
     */
    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 每个请求都带浏览器 UA，响应 BOM 被去掉
     */
    @Test
    @DisplayName("请求带 User-Agent 且去 BOM")
    void sendsUserAgentAndStripsBom() {
        HttpFetcher fetcher = new HttpFetcher(USER_AGENT, 1000, 3000);

        String text = fetcher.getText(baseUrl() + "/ok");

        assertThat(receivedUserAgents).containsExactly(USER_AGENT);
        assertThat(text).isEqualTo("var r = [];");
    }

    /**
     * 非 200 与连接失败都转成具名异常，不重试
     */
    @Test
    @DisplayName("失败转具名异常且不重试")
    void failureThrowsWithoutRetry() {
        HttpFetcher fetcher = new HttpFetcher(USER_AGENT, 1000, 3000);

        assertThatThrownBy(() -> fetcher.getText(baseUrl() + "/error"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态码异常");
        assertThatThrownBy(() -> fetcher.getText("http://127.0.0.1:1/ok"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求失败");
    }

    /**
     * 拼出本地服务端地址
     *
     * @return 基地址
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
