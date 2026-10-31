package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 当日调度总览，四个时点的状态、计数与时钟跳变提示集中在此
 */
@Data
public class TaskOverviewVO implements Serializable {

    /**
     * 统计日期
     */
    private LocalDate tradeDate;

    /**
     * 是否交易日
     */
    private Boolean tradingDay;

    /**
     * 非交易日原因，交易日为空
     */
    private String tradingDayNote;

    /**
     * 逐时点状态，按应发时刻排序
     */
    private List<TaskPointStatusVO> items;

    /**
     * 已成功（含已补发）的时点数
     */
    private Integer sentCount;

    /**
     * 失败的时点数
     */
    private Integer failedCount;

    /**
     * 已过应发时刻仍未成功的时点数
     */
    private Integer overdueCount;

    /**
     * 是否刚检测到时钟跳变
     */
    private Boolean clockJump;

    /**
     * 最近一次跳变的偏差秒数
     */
    private Long clockJumpSeconds;

    /**
     * 整体提示，可直接显示在页面顶部
     */
    private String notice;

    /**
     * 本地邮件留存目录的绝对路径，供调度页直接说明「失败件在哪」
     */
    private String mailArchiveDir;

    /**
     * 尚未补发的失败件数量
     */
    private Integer mailPendingResendCount;

    /**
     * 最近的邮件留存清单，含已发送与未送达件，按时间倒序
     */
    private List<MailArchiveVO> mailArchives;

    private static final long serialVersionUID = 1L;
}
