package com.lilac.enums;

/**
 * 发送留痕状态
 */
public enum SendStatus {

    /**
     * 到点未发，等待发送
     */
    PENDING,

    /**
     * 已发送
     */
    SENT,

    /**
     * 发送失败，内容已留存
     */
    FAILED,

    /**
     * 启动补跑后发送成功
     */
    RESENT
}
