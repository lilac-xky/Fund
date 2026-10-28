package com.lilac.adapter.http;

import java.util.Map;

/**
 * 二进制取数接口，供 Excel 等不能按字符集解码的响应使用
 */
public interface BinaryFetcher {

    /**
     * 取回原始字节与状态码，网络层失败才抛异常
     *
     * @param url     请求地址
     * @param headers 附加请求头
     * @return 二进制响应
     */
    BinaryResponse fetch(String url, Map<String, String> headers);
}
