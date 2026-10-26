package com.lilac.domain.result;

import com.alibaba.fastjson.annotation.JSONField;
import com.lilac.enums.HttpsCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果
 */
@Data
public class Result<T> implements Serializable {
    @JSONField(ordinal = 1)
    private Integer code;
    @JSONField(ordinal = 2)
    private String msg;
    @JSONField(ordinal = 3)
    private T data;

    /**
     * 私有构造，统一由静态方法创建
     *
     * @param code 状态码
     * @param msg  提示信息
     * @param data 业务数据
     */
    private Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功并携带数据
     *
     * @param data 业务数据
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(HttpsCodeEnum.SUCCESS.getCode(), HttpsCodeEnum.SUCCESS.getMsg(), data);
    }

    /**
     * 成功且无数据
     *
     * @return Result
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 按状态码枚举返回错误
     *
     * @param httpsCodeEnum 状态码枚举
     * @return Result
     */
    public static <T> Result<T> error(HttpsCodeEnum httpsCodeEnum) {
        return new Result<>(httpsCodeEnum.getCode(), httpsCodeEnum.getMsg(), null);
    }

    /**
     * 按自定义状态码与消息返回错误
     *
     * @param code 状态码
     * @param msg  提示信息
     * @return Result
     */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 按状态码枚举与自定义消息返回错误
     *
     * @param httpsCodeEnum 状态码枚举
     * @param customMessage 自定义消息
     * @return Result
     */
    public static <T> Result<T> error(HttpsCodeEnum httpsCodeEnum, String customMessage) {
        return new Result<>(httpsCodeEnum.getCode(), customMessage, null);
    }
}