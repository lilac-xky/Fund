package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单个标的的失败明细，供页面看出「哪个代码在失败、为什么」
 */
@Data
public class SourceFailureTargetVO implements Serializable {

    /**
     * 数据源名
     */
    private String source;

    /**
     * 取数目标，如 sh000300
     */
    private String target;

    /**
     * 失败类型
     */
    private String failureKind;

    /**
     * 失败次数
     */
    private Integer count;

    /**
     * 最近一次原因
     */
    private String latestMessage;

    /**
     * 最近一次时间
     */
    private LocalDateTime latestAt;

    private static final long serialVersionUID = 1L;
}
