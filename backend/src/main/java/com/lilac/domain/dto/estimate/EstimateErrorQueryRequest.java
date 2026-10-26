package com.lilac.domain.dto.estimate;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 误差历史查询请求，各字段为空时不参与过滤
 */
@Data
public class EstimateErrorQueryRequest implements Serializable {

    /**
     * 品种业务主键
     */
    private String varietyKey;

    /**
     * 区间起始日，含
     */
    private LocalDate startDate;

    /**
     * 区间截止日，含
     */
    private LocalDate endDate;

    /**
     * 是否超阈值
     */
    private Boolean overThreshold;

    private static final long serialVersionUID = 1L;
}
