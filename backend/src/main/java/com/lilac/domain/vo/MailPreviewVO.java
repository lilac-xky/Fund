package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮件正文预览，HTML 与纯文本同时给出，便于发信前确认排版
 */
@Data
public class MailPreviewVO implements Serializable {

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * HTML 正文
     */
    private String html;

    /**
     * 纯文本兜底正文
     */
    private String text;

    private static final long serialVersionUID = 1L;
}
