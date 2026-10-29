package com.lilac.domain.dto.auth;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求，用户名与口令都由调用方输入
 */
@Data
public class LoginRequest implements Serializable {

    /**
     * 用户名
     */
    private String username;

    /**
     * 口令，明文传输但只在 HTTPS 下，服务端只与密钥文件比对，不落库不记日志
     */
    private String password;

    private static final long serialVersionUID = 1L;
}
