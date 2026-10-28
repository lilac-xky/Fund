package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮件通道状态，只说明是否就绪与缺什么，绝不携带授权码内容
 */
@Data
public class MailStatusVO implements Serializable {

    /**
     * 是否具备发送条件
     */
    private Boolean ready;

    /**
     * 状态说明，未就绪时逐项列出缺什么
     */
    private String note;

    private static final long serialVersionUID = 1L;
}
