package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 技术指标总览，逐只给出三指标与信号，并附本次生效的参数
 */
@Data
public class IndicatorAdvisoryVO implements Serializable {

    /**
     * 逐只指标结果
     */
    private List<IndicatorVO> items;

    /**
     * 无法计算的标的，逐只带原因
     */
    private List<IndicatorVO> unavailableItems;

    /**
     * 本次纳入的标的数
     */
    private Integer requested;

    /**
     * 算出指标的标的数
     */
    private Integer completeCount;

    /**
     * 无法计算的标的数
     */
    private Integer unavailableCount;

    /**
     * 触发信号条数
     */
    private Integer signalCount;

    /**
     * 参数是否可用，false 时整批不算指标
     */
    private Boolean paramUsable;

    /**
     * 参数问题清单
     */
    private List<String> paramIssues;

    /**
     * 本次生效的参数摘要
     */
    private String paramSummary;

    /**
     * 指标信号是否参与收盘前邮件
     */
    private Boolean participateInAlert;

    /**
     * 逐条摘要，供邮件直接取用
     */
    private List<String> summary;

    /**
     * 操作提示，如无持仓
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}
