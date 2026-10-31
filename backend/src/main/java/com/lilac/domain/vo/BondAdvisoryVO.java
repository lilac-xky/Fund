package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 债券监控总览，逐只给出横盘与趋势结论，触发换仓提醒的单独成组
 */
@Data
public class BondAdvisoryVO implements Serializable {

    /**
     * 触发换仓提醒的基金
     */
    private List<BondMonitorVO> items;

    /**
     * 未触发提醒的基金，逐只带出当前数值
     */
    private List<BondMonitorVO> idleItems;

    /**
     * 本次纳入的基金数
     */
    private Integer requested;

    /**
     * 触发提醒的基金数
     */
    private Integer alertCount;

    /**
     * 未触发的基金数
     */
    private Integer idleCount;

    /**
     * 数据不足无法判断的基金数
     */
    private Integer unavailableCount;

    /**
     * 参数是否可用，false 时整批不给结论
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单
     */
    private List<String> paramIssues;

    /**
     * 横盘天数阈值，供页面回显
     */
    private Integer sidewaysDaysThreshold;

    /**
     * 转弱斜率阈值，百分点
     */
    private BigDecimal weakSlopeThreshold;

    /**
     * 逐条摘要，供邮件直接取用
     */
    private List<String> summary;

    /**
     * 操作提示，如无债券持仓
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}
