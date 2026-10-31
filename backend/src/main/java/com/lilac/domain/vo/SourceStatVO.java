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
     * 失败次数
     */
    private Integer failureCount;

    /**
     * 失败率，百分点
     */
    private BigDecimal failureRate;

    /**
     * 出现过的失败类型
     */
    private List<String> failures;

    /**
     * 最近一次事件时间
     */
    private LocalDateTime lastHappenedAt;

    private static final long serialVersionUID = 1L;
}
