package com.lilac.adapter.http;

import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 统一 HTTP 取数封装，固定浏览器 User-Agent、连接与读超时，失败只抛异常不重试
 */
@Slf4j
@Component
public class HttpFetcher implements TextFetcher {

    private final HttpClient httpClient;
    private final String userAgent;
    private final Duration readTimeout;

    /**
     * 按配置构建共享的 HttpClient
     *
     * @param userAgent        浏览器 User-Agent
     * @param connectTimeoutMs 连接超时毫秒
     * @param readTimeoutMs    读取超时毫秒
     */
    public HttpFetcher(@Value("${fund.adapter.http.user-agent}") String userAgent,
                       @Value("${fund.adapter.http.connect-timeout-ms:5000}") long connectTimeoutMs,
                       @Value("${fund.adapter.http.read-timeout-ms:15000}") long readTimeoutMs) {
        this.userAgent = userAgent;
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 按 UTF-8 取回文本
     *
     * @param url 请求地址
     * @return 响应文本，已去掉 BOM
     */
    @Override
    public String getText(String url) {
        return getText(url, StandardCharsets.UTF_8, Map.of());
    }

    /**
     * 按指定字符集取回文本
     *
     * @param url     请求地址
     * @param charset 响应字符集
     * @return 响应文本，已去掉 BOM
     */
    @Override
    public String getText(String url, Charset charset) {
        return getText(url, charset, Map.of());
    }

    /**
     * 按指定字符集与附加请求头取回文本，请求失败不重试
     *
     * @param url     请求地址
     * @param charset 响应字符集
     * @param headers 附加请求头
     * @return 响应文本，已去掉 BOM
     */
    @Override
    public String getText(String url, Charset charset, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .GET();
        // fundf10 系列接口不带 Referer 会返回 404，调用方按需通过 headers 补
        headers.forEach(builder::header);
        return send(builder.build(), charset);
    }

    /**
     * 发出请求并把失败统一转成业务异常
     *
     * @param request 请求
     * @param charset 响应字符集
     * @return 响应文本，已去掉 BOM
     */
    private String send(HttpRequest request, Charset charset) {
        long start = System.currentTimeMillis();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            long cost = System.currentTimeMillis() - start;
            // 每次对外请求都留一行日志，便于核对 UA 与超时是否生效
            log.info("外部请求 {} 状态 {} 耗时 {}ms 字节 {}",
                    request.uri(), response.statusCode(), cost, response.body().length);
            if (response.statusCode() != 200) {
                throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR,
                        "外部数据源状态码异常 " + response.statusCode());
            }
            return stripBom(new String(response.body(), charset));
        } catch (IOException e) {
            log.warn("外部请求失败 {}：{}", request.uri(), e.getMessage());
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "外部数据源请求失败，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "外部数据源请求被中断");
        }
    }

    /**
     * 去掉 UTF-8 BOM，避免首字符污染解析
     *
     * @param text 原始文本
     * @return 去 BOM 文本
     */
    private String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
