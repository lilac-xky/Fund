package com.lilac.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.lilac.domain.result.Result;
import com.lilac.enums.HttpsCodeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理测试，验证请求体解析失败的提示
 */
class GlobalExceptionHandlerTest {

    /**
     * 日期格式非法时返回可读的格式提示
     */
    @Test
    @DisplayName("日期格式非法时指出期望格式")
    void badDateReturnsReadableReason() {
        InvalidFormatException cause = InvalidFormatException.from(null,
                "日期格式错误", "2026-6-5", LocalDate.class);
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("日期解析失败", cause, null);

        Result<Void> result = new GlobalExceptionHandler().handleMessageNotReadable(exception);

        assertThat(result.getCode()).isEqualTo(HttpsCodeEnum.PARAMS_ERROR.getCode());
        assertThat(result.getMsg()).contains("yyyy-MM-dd");
    }
}
