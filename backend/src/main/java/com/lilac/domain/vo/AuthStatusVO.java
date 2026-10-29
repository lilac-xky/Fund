package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录状态，只回「是否已登录」与剩余可用信息，绝不回显口令
 */
@Data
public class AuthStatusVO implements Serializable {

    /**
     * 是否已登录
     */
    private Boolean authenticated;

    /**
     * 登录开关是否开启，关闭时页面不做跳转
     */
    private Boolean required;

    /**
     * 凭据是否已配置，未配置时任何人都登不进来
     */
    private Boolean configured;

    /**
     * 会话剩余有效秒数，未登录为 0
     */
    private Long remainingSeconds;

    private static final long serialVersionUID = 1L;
}
