package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * SMTP 连接测试结果，不携带授权码或本地配置路径
 */
@Data
public class MailTestVO implements Serializable {

    /**
     * 连接测试是否成功
     */
    private Boolean ok;

    /**
     * 测试结果说明
     */
    private String message;

    private static final long serialVersionUID = 1L;
}
