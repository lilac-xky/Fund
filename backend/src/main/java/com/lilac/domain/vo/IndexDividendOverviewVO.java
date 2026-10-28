package com.lilac.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 指数股息率总览，把最新值与数据日期、是否过期一起返回
 */
@Data
public class IndexDividendOverviewVO implements Serializable {

    /**
     * 指数代码
     */
    private String indexCode;

    /**
     * 指数中文全称
     */
    private String indexName;

    /**
     * 最新数据日期，页面与邮件必须显示
     */
    private LocalDate dataDate;

    /**
     * 最新股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 数据来源，取最新一期
     */
    private String sourceLabel;

    /**
     * 距今已过天数
     */
    private Long ageDays;

    /**
     * 是否已过期，过期时不得据此给出加减仓动作
     */
    private Boolean stale;

    /**
     * 过期说明，未过期时为 null
     */
    private String staleReason;

    /**
     * 自动抓取是否可用，国证系列为 false
     */
    private Boolean autoAvailable;

    /**
     * 关联的基金代码列表，即跟踪该指数的持仓
     */
    private List<String> fundCodes;

    /**
     * 近期序列，按日期降序
     */
    private List<IndexDividendVO> history;

    private static final long serialVersionUID = 1L;
}
