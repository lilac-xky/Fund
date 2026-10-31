package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 操作日志的一条，把提醒发送与买卖变动统一成同一形状，便于合并时间线与导出
 */
@Data
public class OperationLogVO implements Serializable {

    /**
     * 日志来源，SEND 提醒 / CHANGE 买卖
     */
    private String source;

    /**
     * 来源中文名
     */
    private String sourceLabel;

    /**
     * 业务日期，排序与筛选都用它
     */
    private LocalDate tradeDate;

    /**
     * 发生时刻，买卖可能只有日期，此时取当日零点
     */
    private LocalDateTime happenedAt;

    /**
     * 事件类型，如「收盘前提醒」「买入」「卖出」
     */
    private String eventType;

    /**
     * 品种业务主键，提醒类为空
     */
    private String varietyKey;

    /**
     * 6 位代码
     */
    private String varietyCode;

    /**
     * 标的名称
     */
    private String varietyName;

    /**
     * 结果或状态，如「已发送」「失败」「成功」
     */
    private String status;

    /**
     * 份额，买卖时有值
     */
    private BigDecimal shares;

    /**
     * 成交单价，买卖时有值
     */
    private BigDecimal unitPrice;

    /**
     * 金额，买卖时为份额乘单价
     */
    private BigDecimal amount;

    /**
     * 说明，失败原因或备注
     */
    private String note;

    private static final long serialVersionUID = 1L;
}
