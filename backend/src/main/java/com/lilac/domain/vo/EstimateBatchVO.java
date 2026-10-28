package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 预估总览，可预估与不可预估分两组，不可预估的绝不参与整体涨跌统计
 */
@Data
public class EstimateBatchVO implements Serializable {

    /**
     * 可预估的基金，按预估涨跌幅降序
     */
    private List<EstimateVO> items;

    /**
     * 不可预估的基金，逐只带出原因
     */
    private List<EstimateVO> unavailableItems;

    /**
     * 本次预估的基金数
     */
    private Integer requested;

    /**
     * 可预估的基金数
     */
    private Integer estimableCount;

    /**
     * 不可预估的基金数
     */
    private Integer unavailableCount;

    /**
     * 整体预估涨跌幅，按持仓等权平均，百分点；只统计可预估且为 A 档的基金
     */
    private BigDecimal overallChange;

    /**
     * 参与整体统计的基金数
     */
    private Integer overallCount;

    /**
     * 行情数据日期
     */
    private LocalDate dataDate;

    /**
     * 是否处于交易时段
     */
    private Boolean tradingHours;

    /**
     * 是否有基金走了降级缓存
     */
    private Boolean degraded;

    /**
     * 混合型基金数，页面据此提示「仅方向参考」
     */
    private Integer mixedCount;

    /**
     * 整体涨跌的口径说明，只统计 A 档时给出提示
     */
    private String overallNote;

    /**
     * 操作提示，如超上限已分批
     */
    private String notice;

    private static final long serialVersionUID = 1L;
}
