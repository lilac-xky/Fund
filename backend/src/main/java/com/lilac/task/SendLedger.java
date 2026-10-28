package com.lilac.task;

import com.lilac.domain.dto.send.SendRecordQueryRequest;
import com.lilac.domain.dto.send.SendRecordSaveRequest;
import com.lilac.domain.vo.SendRecordVO;
import com.lilac.enums.SendPoint;
import com.lilac.enums.SendStatus;
import com.lilac.enums.TaskTrigger;
import com.lilac.service.SendRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 发送留痕读写，按时点记录状态、时间与失败原因，页面与补发都从这里取数
 */
@Slf4j
@Component
public class SendLedger {

    private static final int FAIL_REASON_MAX = 500;

    private final SendRecordService sendRecordService;

    /**
     * 注入留痕业务
     *
     * @param sendRecordService 留痕业务
     */
    public SendLedger(SendRecordService sendRecordService) {
        this.sendRecordService = sendRecordService;
    }

    /**
     * 查询指定日期某个时点的留痕
     *
     * @param date  日期
     * @param point 时点
     * @return 留痕记录，无则空
     */
    public Optional<SendRecordVO> find(LocalDate date, SendPoint point) {
        return list(date).stream().filter(item -> item.getSendPoint() == point).findFirst();
    }

    /**
     * 查询指定日期的全部留痕
     *
     * @param date 日期
     * @return 留痕记录
     */
    public List<SendRecordVO> list(LocalDate date) {
        SendRecordQueryRequest query = new SendRecordQueryRequest();
        query.setSendDate(date);
        List<SendRecordVO> rows = sendRecordService.listSendRecord(query);
        return rows == null ? List.of() : rows;
    }

    /**
     * 写入成功状态，补跑与补发统一记为已补发
     *
     * @param date        日期
     * @param point       时点
     * @param trigger     触发来源
     * @param contentPath 内容留存路径，无正文时为空
     * @return 是否写入成功
     */
    public boolean markSent(LocalDate date, SendPoint point, TaskTrigger trigger, String contentPath) {
        SendStatus status = trigger == TaskTrigger.SCHEDULED ? SendStatus.SENT : SendStatus.RESENT;
        return write(date, point, status, null, contentPath);
    }

    /**
     * 写入失败状态并保存失败原因，失败原因截断到列宽
     *
     * @param date        日期
     * @param point       时点
     * @param failReason  失败原因
     * @param contentPath 内容留存路径
     * @return 是否写入成功
     */
    public boolean markFailed(LocalDate date, SendPoint point, String failReason, String contentPath) {
        return write(date, point, SendStatus.FAILED, truncate(failReason), contentPath);
    }

    /**
     * 写入待发状态，标记该时点已经开工但尚未产出结果
     *
     * @param date  日期
     * @param point 时点
     * @return 是否写入成功
     */
    public boolean markPending(LocalDate date, SendPoint point) {
        return write(date, point, SendStatus.PENDING, null, null);
    }

    /**
     * 判断该时点是否已经成功，成功的不再重复产出
     *
     * @param date  日期
     * @param point 时点
     * @return 已成功返回 true
     */
    public boolean alreadySent(LocalDate date, SendPoint point) {
        return find(date, point)
                .map(item -> item.getStatus() == SendStatus.SENT || item.getStatus() == SendStatus.RESENT)
                .orElse(false);
    }

    /**
     * 判断该时点是否已失败，失败的需要在下次成功发送时带上补发
     *
     * @param date  日期
     * @param point 时点
     * @return 已失败返回 true
     */
    public boolean alreadyFailed(LocalDate date, SendPoint point) {
        return find(date, point).map(item -> item.getStatus() == SendStatus.FAILED).orElse(false);
    }

    /**
     * 落一条留痕，写入异常只记日志，不让留痕本身阻断产出
     *
     * @param date        日期
     * @param point       时点
     * @param status      状态
     * @param failReason  失败原因
     * @param contentPath 内容留存路径
     * @return 是否写入成功
     */
    private boolean write(LocalDate date, SendPoint point, SendStatus status, String failReason,
                          String contentPath) {
        SendRecordSaveRequest request = new SendRecordSaveRequest();
        request.setSendDate(date);
        request.setSendPoint(point);
        request.setStatus(status);
        request.setFailReason(failReason);
        request.setContentPath(contentPath);
        // 只有真正发出去的状态才有发送时间，待发与失败留空以便页面区分
        if (status == SendStatus.SENT || status == SendStatus.RESENT) {
            request.setSentTime(LocalDateTime.now());
        }
        try {
            return sendRecordService.saveSendRecord(request);
        } catch (RuntimeException e) {
            log.warn("发送留痕写入失败 {} {} {}：{}", date, point, status, e.getMessage());
            return false;
        }
    }

    /**
     * 截断失败原因到列宽，避免超长异常信息写库失败
     *
     * @param reason 原始原因
     * @return 截断后的原因
     */
    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= FAIL_REASON_MAX ? reason : reason.substring(0, FAIL_REASON_MAX);
    }
}
