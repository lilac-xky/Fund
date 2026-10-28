package com.lilac.strategy.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一次加减仓判断需要的股息率信号，新鲜度由 ISSUE-009 判定后带过来
 */
@Data
public class DividendSignal {

    /**
     * 策略使用的股息率，单位百分点
     */
    private BigDecimal dividendYield;

    /**
     * 股息率的数据日期，页面与邮件必须显示
     */
    private LocalDate dataDate;

    /**
     * 距今已过天数
     */
    private Long ageDays;

    /**
     * 是否已过期，过期时不得给出加减仓动作
     */
    private Boolean stale;

    /**
     * 过期原因，未过期时为 null
     */
    private String staleReason;
}
