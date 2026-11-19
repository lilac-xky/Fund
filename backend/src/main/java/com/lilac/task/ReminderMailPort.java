package com.lilac.task;

import com.lilac.domain.vo.CalibrationResultVO;
import com.lilac.domain.vo.EstimateBatchVO;
import com.lilac.enums.SendPoint;

import java.time.LocalDate;

/**
 * 时点提醒邮件的出口，四个时点各发一封，具体组装与 SMTP 发送由邮件实现提供
 */
public interface ReminderMailPort {

    /**
     * 产出并发送指定时点的提醒邮件
     *
     * @param point       时点
     * @param tradeDate   交易日
     * @param estimate    当日预估总览，晚上时点为空
     * @param calibration 晚间校准结果，其余时点为空
     * @param resend      是否携带此前未送达内容的补发
     * @return 邮件内容留存路径，无正文时为空
     * @throws ReminderDeliveryException 发送失败时抛出，带内容留存路径，由编排层记入留痕
     */
    String send(SendPoint point, LocalDate tradeDate, EstimateBatchVO estimate,
                CalibrationResultVO calibration, boolean resend);
}
