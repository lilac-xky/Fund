package com.lilac.domain.dto.send;

import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发送留痕保存请求
 */
@Data
public class SendRecordSaveRequest implements Serializable {

    /**
     * 发送日期
     */
    private LocalDate sendDate;

    /**
     * 发送时点
     */
    private SendPoint sendPoint;

    /**
     * 发送状态
     */
    private SendStatus status;

    /**
     * 实际发送时间
     */
    private LocalDateTime sentTime;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 邮件内容留存路径
     */
    private String contentPath;

    private static final long serialVersionUID = 1L;
}
