package com.lilac.exception;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.lilac.domain.result.Result;
import com.lilac.enums.HttpsCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理自定义的业务异常
     *
     * @param e 业务异常
     * @return Result
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleSystemException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体解析失败，指出具体字段与期望格式
     *
     * @param e 解析异常
     * @return Result
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(HttpsCodeEnum.PARAMS_ERROR, readableReason(e));
    }

    /**
     * 处理所有未被捕获的未知异常
     *
     * @param e 未知异常
     * @return Result
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未捕获的系统异常！ ", e);
        // 对外屏蔽内部细节，返回统一的系统错误提示
        return Result.error(HttpsCodeEnum.SYSTEM_ERROR);
    }

    /**
     * 从解析异常中提取可读原因，指向字段名与期望格式
     *
     * @param e 解析异常
     * @return 可读原因
     */
    private String readableReason(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException format) {
            String field = format.getPath().stream()
                    .map(Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            Class<?> target = format.getTargetType();
            if (target == LocalDate.class || target == LocalDateTime.class || target == LocalTime.class) {
                String expected = target == LocalDate.class ? "yyyy-MM-dd（如 2026-10-28）"
                        : target == LocalDateTime.class ? "yyyy-MM-dd HH:mm:ss" : "HH:mm:ss";
                return field.isBlank() ? "日期或时间格式不正确，应为 " + expected
                        : "字段 " + field + " 格式不正确，应为 " + expected;
            }
            if (target.isEnum()) {
                return field.isBlank() ? "参数取值不在可选项内，请检查后再提交"
                        : "字段 " + field + " 的取值不在可选项内";
            }
        }
        return HttpsCodeEnum.PARAMS_ERROR.getMsg();
    }
}
