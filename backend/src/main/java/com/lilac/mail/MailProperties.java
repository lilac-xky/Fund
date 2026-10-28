package com.lilac.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件参数，收件人、发件人与 SMTP 服务器在此，授权码单独放 secrets.env 不进配置文件
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
     * 收件地址，可多个
     */
    private List<String> recipients = new ArrayList<>();

    /**
     * 发件地址，为空时取 SMTP 账号
     */
    private String from;

    /**
     * SMTP 主机
     */
    private String host;

    /**
     * SMTP 端口，465 走 SSL、587 走 STARTTLS
     */
    private int port = 465;

    /**
     * SMTP 账号
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
