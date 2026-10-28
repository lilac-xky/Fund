package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮件设置视图，授权码字段只表示「已配置 / 未配置」，永久不含明文
 */
@Data
public class MailSettingVO implements Serializable {

    /**
     * 收件地址，多个用逗号分隔
     */
    private String recipients;

    /**
     * SMTP 主机
     */
    private String host;

    /**
     * SMTP 端口
     */
    private String port;

    /**
     * SMTP 账号
     */
    private String username;

    /**
     * 发件地址
     */
    private String from;

    /**
     * 授权码是否已配置，只回布尔值
     */
    private Boolean passwordConfigured;

    /**
     * 通道是否具备发送条件
     */
    private Boolean ready;

    /**
     * 状态说明，未就绪时逐项列出缺什么
     */
    private String note;

    /**
     * 当前配置是否可以进行连接测试
     */
    private Boolean testable;

    /**
     * 最近一次连接测试结果
     */
    private String testNote;

    private static final long serialVersionUID = 1L;
}
