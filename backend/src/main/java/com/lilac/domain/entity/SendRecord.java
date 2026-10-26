package com.lilac.domain.entity;

import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发送留痕，每个交易日每个时点一条记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "send_record", camelToUnderline = false, comment = "发送留痕，每个交易日每个时点一条记录")
public class SendRecord extends BaseEntity {

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
     * 实际发送时间，未发送时为空
     */
    private LocalDateTime sentTime;

    /**
     * 失败原因，仅在失败时填写
     */
    private String failReason;

    /**
     * 邮件内容留存路径，相对 data 目录
     */
    private String contentPath;
}
