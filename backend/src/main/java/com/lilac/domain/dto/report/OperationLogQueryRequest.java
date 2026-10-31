package com.lilac.domain.dto.report;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 操作日志查询请求，日期为空时取最近 30 天
 */
@Data
public class OperationLogQueryRequest implements Serializable {

    /**
     * 区间起始日，含
     */
    private LocalDate startDate;

    /**
     * 区间截止日，含
     */
    private LocalDate endDate;

    /**
     * 只看某一品种
     */
    private String varietyKey;

    /**
     * 只看来某一类来源 SEND / CHANGE，为空表示全部
     */
    private String source;

    private static final long serialVersionUID = 1L;
}
