package com.lilac.adapter.http;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 二进制响应，带状态码与内容类型，供调用方区分「没这个资源」与「源坏了」
 */
@Data
@AllArgsConstructor
public class BinaryResponse {

    /**
     * HTTP 状态码
     */
    private int status;

    /**
     * 响应字节
     */
    private byte[] body;

    /**
     * 响应内容类型
     */
    private String contentType;

    /**
     * 是否请求成功
     *
     * @return 2xx 返回 true
     */
    public boolean ok() {
        return status >= 200 && status < 300;
    }
}
