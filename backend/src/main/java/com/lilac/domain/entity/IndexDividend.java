package com.lilac.domain.entity;

import com.lilac.enums.DividendSource;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 指数股息率，按指数与数据日期唯一，是红利加减仓建议的唯一输入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "index_dividend", camelToUnderline = false, comment = "指数股息率，按指数与数据日期唯一")
public class IndexDividend extends BaseEntity {

    /**
     * 指数代码，如 000922
     */
    private String indexCode;

    /**
     * 指数中文全称
     */
    private String indexName;

    /**
     * 数据日期，页面与邮件必须显示，过期不参与加减仓建议
     */
    private LocalDate dataDate;

    /**
     * 策略使用的股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 股息率1（总股本），单位百分点
     */
    private BigDecimal dividendYield1;

    /**
     * 股息率2（计算用股本），单位百分点
     */
    private BigDecimal dividendYield2;

    /**
     * 数据来源，自动抓取或手工覆盖
     */
    private DividendSource source;
}
