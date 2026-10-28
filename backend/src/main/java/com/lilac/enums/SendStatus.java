package com.lilac.enums;

import lombok.Getter;

/**
 * 发送留痕状态
 */
@Getter
public enum SendStatus {

    /**
     * 到点未发，等待发送
     */
    PENDING("待发"),

    /**
     * 已发送
     */
    SENT("已发送"),

    /**
     * 发送失败或未发送，内容已留存
     */
    FAILED("未送达"),

    /**
     * 补跑或补发后发送成功
     */
    RESENT("已补发");

    /**
     * 中文名称，供页面与邮件回显
     */
    private final String label;

    SendStatus(String label) {
        this.label = label;
    }
}
