package com.lilac.exception;

import com.lilac.enums.HttpsCodeEnum;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    /**
     * 按状态码枚举构造异常
     *
     * @param httpsCodeEnum 状态码枚举
     */
    public BusinessException(HttpsCodeEnum httpsCodeEnum) {
        super(httpsCodeEnum.getMsg());
        this.code = httpsCodeEnum.getCode();
    }

    /**
     * 按状态码枚举与自定义消息构造异常
     *
     * @param httpsCodeEnum 状态码枚举
     * @param customMessage 自定义消息
     */
    public BusinessException(HttpsCodeEnum httpsCodeEnum, String customMessage) {
        super(customMessage);
        this.code = httpsCodeEnum.getCode();
    }
}