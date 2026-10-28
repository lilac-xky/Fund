package com.lilac.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件参数，只留非身份项；账号、收件人与授权码统一放 secrets.env，不进需要提交的配置文件
 */
@Data
@Component
@ConfigurationProperties(prefix = "fund.mail")
public class MailProperties {

    /**
     * 邮件开关，关闭时不发送也不留存，收盘前时点直接报失败说明
     */
    private boolean enabled = true;

    /**
     * 收件地址兜底值，留空时以密钥文件的 SMTP_RECIPIENTS 为准
     */
    private List<String> recipients = new ArrayList<>();

    /**
     * 发件地址兜底值，留空时以密钥文件为准，再为空则取账号
     */
    private String from;

    /**
     * SMTP 主机兜底值，留空时以密钥文件的 SMTP_HOST 为准
     */
    private String host;

    /**
     * SMTP 端口兜底值，密钥文件的 SMTP_PORT 优先；587 走 STARTTLS、465 走 SSL
     */
    private int port = 587;

    /**
     * SMTP 账号兜底值，留空时以密钥文件的 SMTP_USERNAME 为准
     */
    private String username;

    /**
     * 密钥文件路径，授权码只从这里读，任何接口都不回显
     */
    private String secretsFile = "config/secrets.env";

    /**
     * 连接与读取超时，毫秒；邮件服务不可用时要快速失败，不能拖住收盘前时点
     */
    private int timeoutMillis = 10000;

    /**
     * 邮件正文里必须同屏出现的误差说明，多处引用同一份文案
     */
    private String disclaimer = "预估基于重仓股与指数推算，与实际净值存在误差";
}
