package com.lilac.adapter.http;

import java.nio.charset.Charset;

/**
 * 文本取数接口，屏蔽具体 HTTP 实现以便替换与测试
 */
public interface TextFetcher {

    /**
     * 按 UTF-8 取回文本
     *
     * @param url 请求地址
     * @return 响应文本
     */
    String getText(String url);

    /**
     * 按指定字符集取回文本
     *
     * @param url     请求地址
     * @param charset 响应字符集
     * @return 响应文本
     */
    String getText(String url, Charset charset);
}
