package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一份本地邮件留存的摘要，供调度页列出已发送与未送达件
 */
@Data
public class MailArchiveVO implements Serializable {

    /**
     * 留存文件名
     */
    private String fileName;

    /**
     * 主题
     */
    private String subject;

    /**
     * 交易日
     */
    private String tradeDate;

    /**
     * 发送时间，文件头部记录
     */
    private String sentAt;

    /**
     * 是否发送失败件
     */
    private Boolean failed;

    /**
     * 是否已补发过
     */
    private Boolean resent;

    /**
     * 文件大小，字节
     */
    private Long sizeBytes;

    /**
     * 留存时间，取文件修改时间
     */
    private LocalDateTime archivedAt;

    private static final long serialVersionUID = 1L;
}
