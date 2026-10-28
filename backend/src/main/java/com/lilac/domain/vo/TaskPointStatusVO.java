package com.lilac.domain.vo;

import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 单时点状态，页面据此显示「今日 14:30 已发送 / 未发送 / 已补发」
 */
@Data
public class TaskPointStatusVO implements Serializable {

    /**
     * 时点
     */
    private SendPoint point;

    /**
     * 时点中文名
     */
    private String pointLabel;

    /**
     * 应发时刻
     */
    private LocalTime dueTime;

    /**
     * 留痕状态，无记录时为空
     */
    private SendStatus status;

    /**
     * 状态中文名，无记录时显示未发送
     */
    private String statusLabel;

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
     * 是否已过应发时刻仍未成功
     */
    private Boolean overdue;

    /**
     * 一句话说明，含应发时刻与状态
     */
    private String summary;

    private static final long serialVersionUID = 1L;
}
