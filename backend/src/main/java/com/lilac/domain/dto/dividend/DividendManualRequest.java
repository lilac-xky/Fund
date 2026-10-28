package com.lilac.domain.dto.dividend;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 手工录入股息率请求，国证系列与自动抓取失败时使用
 */
@Data
public class DividendManualRequest implements Serializable {

    /**
     * 指数代码，如 399321
     */
    private String indexCode;

    /**
     * 数据日期，必须由使用者填真实日期，不允许默认今天
     */
    private LocalDate dataDate;

    /**
     * 股息率，单位百分点，如 4.08 表示 4.08%
     */
    private BigDecimal dividendYield;

    /**
     * 指数中文全称，可空
     */
    private String indexName;

    private static final long serialVersionUID = 1L;
}
