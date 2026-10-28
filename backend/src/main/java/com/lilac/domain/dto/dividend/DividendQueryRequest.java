package com.lilac.domain.dto.dividend;

import lombok.Data;

import java.io.Serializable;

/**
 * 指数股息率查询请求，各字段为空时不参与过滤
 */
@Data
public class DividendQueryRequest implements Serializable {

    /**
     * 指数代码
     */
    private String indexCode;

    /**
     * 基金业务主键，传入时按其跟踪指数查询
     */
    private String varietyKey;

    /**
     * 返回的最近期数，为空时取默认值
     */
    private Integer limit;

    private static final long serialVersionUID = 1L;
}
