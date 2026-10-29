package com.lilac.auth;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录配置，只保留开关、Cookie 与阈值，口令本身在密钥文件里
 */
@Data
@Component
public class AuthProperties {

    /**
     * 登录开关，关闭时不做任何鉴权，仅供本机开发使用
     */
    @Value("${fund.auth.enabled:true}")
    private boolean enabled;

    /**
     * 密钥文件路径，与 SMTP 共用同一个文件
     */
    @Value("${fund.mail.secrets-file:backend/config/secrets.env}")
    private String secretsFile;

    /**
     * 会话有效期，小时；超时后需重新登录
     */
    @Value("${fund.auth.session-hours:12}")
    private int sessionHours;

    /**
     * 连续登录失败多少次后临时锁定
     */
    @Value("${fund.auth.max-failures:5}")
    private int maxFailures;

    /**
     * 锁定时长，分钟
     */
    @Value("${fund.auth.lock-minutes:15}")
    private int lockMinutes;

    /**
     * 会话 Cookie 名称
     */
    @Value("${fund.auth.cookie-name:fund_session}")
    private String cookieName;

    /**
     * 是否只在 HTTPS 下发送 Cookie，生产环境必须为 true
     */
    @Value("${fund.auth.cookie-secure:false}")
    private boolean cookieSecure;
}
