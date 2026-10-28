package com.lilac.domain.dto.setting;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮件与 SMTP 参数保存请求，授权码只进不出，读接口永远不返回它
 */
@Data
public class MailSettingSaveRequest implements Serializable {

    /**
     * 收件地址，多个用逗号分隔
     */
    private String recipients;

    /**
     * SMTP 主机
     */
    private String host;

    /**
     * SMTP 端口，465 走 SSL、587 走 STARTTLS
     */
    private String port;

    /**
     * SMTP 账号
     */
    private String username;

    /**
     * 发件地址，留空时取账号
     */
    private String from;

    /**
     * 授权码，留空表示不改；写入后任何接口都不回显
     */
    private String password;

    /**
     * 是否清除已保存的授权码，为 true 时忽略 password
     */
    private Boolean clearPassword;

    private static final long serialVersionUID = 1L;
}
