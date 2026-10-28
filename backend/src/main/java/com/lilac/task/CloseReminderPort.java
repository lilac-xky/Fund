package com.lilac.task;

import java.time.LocalDate;

/**
 * 收盘前提醒邮件的出口，ISSUE-011 只定义编排用的接口，具体组装与 SMTP 发送由 ISSUE-012 实现
 */
public interface CloseReminderPort {

    /**
     * 产出并发送指定交易日的收盘前提醒
     *
     * @param tradeDate 交易日
     * @param resend    是否携带此前未送达内容的补发
     * @return 邮件内容留存路径，无正文时为空
     * @throws ReminderDeliveryException 发送失败时抛出，带内容留存路径，由编排层记入留痕
     */
    String send(LocalDate tradeDate, boolean resend);
}
