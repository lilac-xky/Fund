package com.lilac.domain.dto.source;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 数据源取数留痕查询请求，日期为空时默认最近 7 天
 */
@Data
public class SourceEventQueryRequest implements Serializable {

    /**
     * 区间起始日，含
     */
    private LocalDate startDate;

    /**
     * 区间截止日，含
     */
    private LocalDate endDate;

    private static final long serialVersionUID = 1L;
}
