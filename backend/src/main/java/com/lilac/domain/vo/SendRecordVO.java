package com.lilac.domain.vo;

import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发送留痕视图对象
 */
@Data
public class SendRecordVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
