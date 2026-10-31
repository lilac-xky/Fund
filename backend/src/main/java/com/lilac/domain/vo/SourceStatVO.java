package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个数据源的取数健康度，失败率用于判断上游是否在改版
 */
@Data
public class SourceStatVO implements Serializable {

    /**
     * 数据源名
     */
    private String source;

    /**
     * 事件总数
     */
    private Integer totalCount;

    /**
     * 成功次数
     */
    private Integer successCount;

    /**
     * 失败次数，不含无数据
     */
    private Integer failureCount;

    /**
     * 无数据次数，标的自身没有行情（停牌、指数推错市场等），不计入失败率
     */
    private Integer noDataCount;

    /**
     * 失败率，百分点，只按真失败（结构变化/限流/网络）计算
     */
    private BigDecimal failureRate;

    /**
     * 出现过的失败类型
     */
    private List<String> failures;

    /**
     * 失败最多的标的明细，供页面直接看出「哪个代码在失败」
     */
    private List<SourceFailureTargetVO> failureTargets;

    /**
     * 最近一次事件时间
     */
    private LocalDateTime lastHappenedAt;

    private static final long serialVersionUID = 1L;
}
