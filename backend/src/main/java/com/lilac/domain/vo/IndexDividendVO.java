package com.lilac.domain.vo;

import com.lilac.enums.DividendSource;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单日股息率视图，日期与新鲜度必须随数值一起给出
 */
@Data
public class IndexDividendVO implements Serializable {

    /**
     * 指数代码
     */
    private String indexCode;

    /**
     * 指数中文全称
     */
    private String indexName;

    /**
     * 数据日期
     */
    private LocalDate dataDate;

    /**
     * 股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 股息率1（总股本）
     */
    private BigDecimal dividendYield1;

    /**
     * 股息率2（计算用股本）
     */
    private BigDecimal dividendYield2;

    /**
     * 数据来源
     */
    private DividendSource source;

    /**
     * 数据来源中文名
     */
    private String sourceLabel;

    private static final long serialVersionUID = 1L;
}
